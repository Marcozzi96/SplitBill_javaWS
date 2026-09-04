package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import it.javaWS.models.dto.ShoppingItemDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.ShoppingItem;
import it.javaWS.repositories.ShoppingItemRepository;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class ShoppingItemServiceTest {

    @Mock
    private ShoppingItemRepository shoppingItemRepository;

    @Mock
    private GroupService groupService;

    @InjectMocks
    private ShoppingItemService shoppingItemService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    void getItemsByGroupDto_withoutFilter_returnsPage() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.findByGroupIdOrderByToBuyDescIdAsc(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        Page<ShoppingItemDTO> page = shoppingItemService.getItemsByGroupDto(10L, 1L, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getName()).isEqualTo("Pane");
        assertThat(page.getContent().getFirst().getGroupId()).isEqualTo(10L);
    }

    @Test
    void getItemsByGroupDto_withToBuyFilter_usesFilteredQuery() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.findByGroupIdAndToBuyOrderByToBuyDescIdAsc(10L, true, pageable))
                .thenReturn(Page.empty(pageable));

        shoppingItemService.getItemsByGroupDto(10L, 1L, true, pageable);

        verify(shoppingItemRepository).findByGroupIdAndToBuyOrderByToBuyDescIdAsc(10L, true, pageable);
        verify(shoppingItemRepository, never()).findByGroupIdOrderByToBuyDescIdAsc(any(), any());
    }

    @Test
    void getItemsByGroupDto_nonMember_throwsAccessDenied() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.getItemsByGroupDto(10L, 1L, null, pageable));
        assertThat(ex.getMessage()).isEqualTo("L'utente non fa parte del gruppo richiesto");
    }

    @Test
    void createItem_success_trimsNameAndSetsDefaults() {
        Group group = createGroup(10L);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupService.getGroup(10L)).thenReturn(group);
        when(shoppingItemRepository.existsByGroupIdAndNameIgnoreCase(10L, "Pane")).thenReturn(false);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingItem item = shoppingItemService.createItem(10L, 1L, "  Pane  ", "integrale");

        assertThat(item.getName()).isEqualTo("Pane");
        assertThat(item.getNote()).isEqualTo("integrale");
        assertThat(item.isToBuy()).isTrue();
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getGroup()).isEqualTo(group);
    }

    @Test
    void createItem_duplicateName_throwsIllegalArgument() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.existsByGroupIdAndNameIgnoreCase(10L, "pane")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shoppingItemService.createItem(10L, 1L, "pane", null));
        assertThat(ex.getMessage()).isEqualTo("Articolo già presente in lista");
        verify(shoppingItemRepository, never()).save(any());
    }

    @Test
    void createItem_blankName_throwsIllegalArgument() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> shoppingItemService.createItem(10L, 1L, "   ", null));
        verify(shoppingItemRepository, never()).save(any());
    }

    @Test
    void createItem_nonMember_throwsAccessDenied() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.createItem(10L, 1L, "Pane", null));
        verify(shoppingItemRepository, never()).save(any());
    }

    @Test
    void updateToBuyDto_togglesBothWays() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingItemDTO dto = shoppingItemService.updateToBuyDto(1L, 1L, false);
        assertThat(dto.isToBuy()).isFalse();

        dto = shoppingItemService.updateToBuyDto(1L, 1L, true);
        assertThat(dto.isToBuy()).isTrue();
    }

    @Test
    void updateToBuyDto_notFound_throwsEntityNotFound() {
        when(shoppingItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> shoppingItemService.updateToBuyDto(99L, 1L, false));
    }

    @Test
    void updateToBuyDto_nonMember_throwsAccessDenied() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.updateToBuyDto(1L, 1L, false));
        verify(shoppingItemRepository, never()).save(any());
    }

    @Test
    void deleteItem_success() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        shoppingItemService.deleteItem(1L, 1L);

        verify(shoppingItemRepository).delete(item);
    }

    @Test
    void deleteItem_nonMember_throwsAccessDenied() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.deleteItem(1L, 1L));
        verify(shoppingItemRepository, never()).delete(any(ShoppingItem.class));
    }

    private Group createGroup(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setName("group" + id);
        return group;
    }

    private ShoppingItem createItem(Long id, String name, Group group) {
        ShoppingItem item = new ShoppingItem();
        item.setId(id);
        item.setName(name);
        item.setToBuy(true);
        item.setGroup(group);
        return item;
    }
}
