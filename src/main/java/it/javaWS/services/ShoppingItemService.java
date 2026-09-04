package it.javaWS.services;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.ShoppingItemDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.ShoppingItem;
import it.javaWS.repositories.ShoppingItemRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class ShoppingItemService {

	private final ShoppingItemRepository shoppingItemRepository;
	private final GroupService groupService;

	public ShoppingItemService(ShoppingItemRepository shoppingItemRepository, GroupService groupService) {
		this.shoppingItemRepository = shoppingItemRepository;
		this.groupService = groupService;
	}

	@Transactional(readOnly = true)
	public Page<ShoppingItemDTO> getItemsByGroupDto(Long groupId, Long userId, Boolean toBuy, Pageable pageable) {
		checkMembership(groupId, userId);
		Page<ShoppingItem> items = toBuy != null
				? shoppingItemRepository.findByGroupIdAndToBuyOrderByToBuyDescIdAsc(groupId, toBuy, pageable)
				: shoppingItemRepository.findByGroupIdOrderByToBuyDescIdAsc(groupId, pageable);
		return items.map(ShoppingItemDTO::new);
	}

	@Transactional
	public ShoppingItem createItem(Long groupId, Long userId, String name, String note) {
		checkMembership(groupId, userId);

		String nomePulito = name != null ? name.trim() : null;
		if (nomePulito == null || nomePulito.isEmpty()) {
			throw new IllegalArgumentException("Il nome dell'articolo non può essere vuoto");
		}
		// Il duplicato è rifiutato anche se l'articolo esistente è già stato acquistato.
		if (shoppingItemRepository.existsByGroupIdAndNameIgnoreCase(groupId, nomePulito)) {
			throw new IllegalArgumentException("Articolo già presente in lista");
		}

		Group group = groupService.getGroup(groupId);

		ShoppingItem item = new ShoppingItem();
		item.setGroup(group);
		item.setName(nomePulito);
		item.setNote(note);
		item.setToBuy(true);
		item.setCreatedAt(LocalDateTime.now());
		return shoppingItemRepository.save(item);
	}

	@Transactional
	public ShoppingItemDTO createItemDto(Long groupId, Long userId, String name, String note) {
		return new ShoppingItemDTO(createItem(groupId, userId, name, note));
	}

	@Transactional(readOnly = true)
	public ShoppingItem getItem(Long id) {
		return shoppingItemRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Articolo non trovato"));
	}

	@Transactional
	public ShoppingItemDTO updateToBuyDto(Long itemId, Long userId, boolean toBuy) {
		ShoppingItem item = getItem(itemId);
		checkMembership(item.getGroup().getId(), userId);
		item.setToBuy(toBuy);
		return new ShoppingItemDTO(shoppingItemRepository.save(item));
	}

	@Transactional
	public void deleteItem(Long itemId, Long userId) {
		ShoppingItem item = getItem(itemId);
		checkMembership(item.getGroup().getId(), userId);
		shoppingItemRepository.delete(item);
	}

	@Transactional(readOnly = true)
	public List<ShoppingItem> getItemsByIds(Collection<Long> ids) {
		return shoppingItemRepository.findByIdIn(ids);
	}

	private void checkMembership(Long groupId, Long userId) {
		if (!groupService.existsByGroupIdAndUserId(groupId, userId)) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
	}
}
