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
import it.javaWS.models.dto.ShoppingItemPositionDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.ShoppingItem;
import it.javaWS.repositories.ShoppingItemRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class ShoppingItemService {

	private static final double INITIAL_POSITION = 1000.0;
	private static final double POSITION_STEP = 1000.0;
	private static final double EPSILON = 1e-6;

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
				? shoppingItemRepository.findByGroupIdAndToBuyOrderByToBuyDescPositionDescIdAsc(groupId, toBuy, pageable)
				: shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(groupId, pageable);
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
		item.setPosition(calculatePositionForNewItem(groupId));
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
		// Lo spunta/ripristina non tocca la position: l'ordine cambia grazie all'ORDER BY.
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

	/**
	 * Sposta un articolo "da acquistare" tra due vicini dello stesso gruppo.
	 * I vicini devono essere anch'essi toBuy=true e dello stesso gruppo.
	 * Se la distanza tra i vicini è troppo piccola, ricalcola le posizioni di tutto il gruppo.
	 *
	 * @param itemId     id dell'articolo da spostare
	 * @param userId     id dell'utente che richiede l'operazione
	 * @param prevItemId id del vicino precedente (null = in cima)
	 * @param nextItemId id del vicino successivo (null = in fondo)
	 * @return lista aggiornata di tutti gli articoli del gruppo con la nuova position
	 */
	@Transactional
	public List<ShoppingItemPositionDTO> reorderItem(Long itemId, Long userId, Long prevItemId, Long nextItemId) {
		ShoppingItem item = getItem(itemId);
		Long groupId = item.getGroup().getId();
		checkMembership(groupId, userId);

		if (!item.isToBuy()) {
			throw new IllegalArgumentException("Non è possibile riordinare un articolo già acquistato");
		}

		ShoppingItem prev = loadAndValidateNeighbor(prevItemId, groupId);
		ShoppingItem next = loadAndValidateNeighbor(nextItemId, groupId);

		double newPosition = calculateNewPosition(item, prev, next);
		item.setPosition(newPosition);
		shoppingItemRepository.save(item);

		return shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(groupId).stream()
				.map(ShoppingItemPositionDTO::new)
				.toList();
	}

	private ShoppingItem loadAndValidateNeighbor(Long neighborId, Long groupId) {
		if (neighborId == null) {
			return null;
		}
		ShoppingItem neighbor = getItem(neighborId);
		if (!neighbor.getGroup().getId().equals(groupId)) {
			throw new IllegalArgumentException("I vicini devono appartenere allo stesso gruppo");
		}
		if (!neighbor.isToBuy()) {
			throw new IllegalArgumentException("I vicini devono essere articoli da acquistare");
		}
		return neighbor;
	}

	private double calculateNewPosition(ShoppingItem item, ShoppingItem prev, ShoppingItem next) {
		if (prev == null && next == null) {
			// Lista con un solo articolo ordinabile: se non ha una position valida, gliela assegna.
			return item.getPosition() == 0.0 ? INITIAL_POSITION : item.getPosition();
		}
		if (prev == null) {
			return next.getPosition() + 1.0;
		}
		if (next == null) {
			return prev.getPosition() - 1.0;
		}

		double prevPosition = prev.getPosition();
		double nextPosition = next.getPosition();
		if (Math.abs(prevPosition - nextPosition) < EPSILON) {
			renormalizeGroupPositions(item.getGroup().getId());
			ShoppingItem refreshedPrev = getItem(prev.getId());
			ShoppingItem refreshedNext = getItem(next.getId());
			return (refreshedPrev.getPosition() + refreshedNext.getPosition()) / 2.0;
		}
		return (prevPosition + nextPosition) / 2.0;
	}

	private void renormalizeGroupPositions(Long groupId) {
		List<ShoppingItem> items = shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(groupId);
		int size = items.size();
		for (int i = 0; i < size; i++) {
			items.get(i).setPosition((size - i) * POSITION_STEP);
		}
		shoppingItemRepository.saveAll(items);
	}

	private double calculatePositionForNewItem(Long groupId) {
		return shoppingItemRepository.findMaxPositionByGroupIdAndToBuyTrue(groupId)
				.map(max -> max + 1.0)
				.orElse(INITIAL_POSITION);
	}

	private void checkMembership(Long groupId, Long userId) {
		if (!groupService.existsByGroupIdAndUserId(groupId, userId)) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
	}
}
