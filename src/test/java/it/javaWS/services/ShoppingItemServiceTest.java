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
import it.javaWS.models.dto.ShoppingItemPositionDTO;
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
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(10L, pageable))
                .thenReturn(new PageImpl<>(List.of(item), pageable, 1));

        Page<ShoppingItemDTO> page = shoppingItemService.getItemsByGroupDto(10L, 1L, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().getName()).isEqualTo("Pane");
        assertThat(page.getContent().getFirst().getGroupId()).isEqualTo(10L);
        assertThat(page.getContent().getFirst().getPosition()).isEqualTo(1000.0);
    }

    @Test
    void getItemsByGroupDto_withToBuyFilter_usesFilteredQuery() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.findByGroupIdAndToBuyOrderByToBuyDescPositionDescIdAsc(10L, true, pageable))
                .thenReturn(Page.empty(pageable));

        shoppingItemService.getItemsByGroupDto(10L, 1L, true, pageable);

        verify(shoppingItemRepository).findByGroupIdAndToBuyOrderByToBuyDescPositionDescIdAsc(10L, true, pageable);
        verify(shoppingItemRepository, never()).findByGroupIdOrderByToBuyDescPositionDescIdAsc(any(), any());
    }

    @Test
    void getItemsByGroupDto_nonMember_throwsAccessDenied() {
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.getItemsByGroupDto(10L, 1L, null, pageable));
        assertThat(ex.getMessage()).isEqualTo("L'utente non fa parte del gruppo richiesto");
    }

    @Test
    void createItem_emptyGroup_assignsInitialPosition() {
        Group group = createGroup(10L);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupService.getGroup(10L)).thenReturn(group);
        when(shoppingItemRepository.existsByGroupIdAndNameIgnoreCase(10L, "Pane")).thenReturn(false);
        when(shoppingItemRepository.findMaxPositionByGroupIdAndToBuyTrue(10L)).thenReturn(Optional.empty());
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingItem item = shoppingItemService.createItem(10L, 1L, "  Pane  ", "integrale");

        assertThat(item.getName()).isEqualTo("Pane");
        assertThat(item.getNote()).isEqualTo("integrale");
        assertThat(item.isToBuy()).isTrue();
        assertThat(item.getPosition()).isEqualTo(1000.0);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getGroup()).isEqualTo(group);
    }

    @Test
    void createItem_existingItems_assignsMaxPlusOne() {
        Group group = createGroup(10L);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupService.getGroup(10L)).thenReturn(group);
        when(shoppingItemRepository.existsByGroupIdAndNameIgnoreCase(10L, "Latte")).thenReturn(false);
        when(shoppingItemRepository.findMaxPositionByGroupIdAndToBuyTrue(10L)).thenReturn(Optional.of(2500.0));
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingItem item = shoppingItemService.createItem(10L, 1L, "Latte", null);

        assertThat(item.getPosition()).isEqualTo(2501.0);
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
    void updateToBuyDto_togglesBothWays_preservesPosition() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1234.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShoppingItemDTO dto = shoppingItemService.updateToBuyDto(1L, 1L, false);
        assertThat(dto.isToBuy()).isFalse();
        assertThat(dto.getPosition()).isEqualTo(1234.0);

        dto = shoppingItemService.updateToBuyDto(1L, 1L, true);
        assertThat(dto.isToBuy()).isTrue();
        assertThat(dto.getPosition()).isEqualTo(1234.0);
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
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.updateToBuyDto(1L, 1L, false));
        verify(shoppingItemRepository, never()).save(any());
    }

    @Test
    void deleteItem_success() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        shoppingItemService.deleteItem(1L, 1L);

        verify(shoppingItemRepository).delete(item);
    }

    @Test
    void deleteItem_nonMember_throwsAccessDenied() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.deleteItem(1L, 1L));
        verify(shoppingItemRepository, never()).delete(any(ShoppingItem.class));
    }

    @Test
    void reorderItem_betweenNeighbors_usesAverage() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        ShoppingItem prev = createItem(2L, "Latte", group, true, 2000.0);
        ShoppingItem next = createItem(3L, "Pasta", group, true, 1000.0);
        stubReorder(item, prev, next, List.of(item, prev, next));

        List<ShoppingItemPositionDTO> result = shoppingItemService.reorderItem(1L, 1L, 2L, 3L);

        assertThat(result).hasSize(3);
        assertThat(item.getPosition()).isEqualTo(1500.0);
    }

    @Test
    void reorderItem_toTop_usesNextPlusOne() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        ShoppingItem next = createItem(3L, "Pasta", group, true, 2500.0);
        stubReorder(item, null, next, List.of(next, item));

        List<ShoppingItemPositionDTO> result = shoppingItemService.reorderItem(1L, 1L, null, 3L);

        assertThat(item.getPosition()).isEqualTo(2501.0);
        assertThat(result).hasSize(2);
    }

    @Test
    void reorderItem_toBottom_usesPrevMinusOne() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 2000.0);
        ShoppingItem prev = createItem(2L, "Latte", group, true, 1000.0);
        stubReorder(item, prev, null, List.of(item, prev));

        List<ShoppingItemPositionDTO> result = shoppingItemService.reorderItem(1L, 1L, 2L, null);

        assertThat(item.getPosition()).isEqualTo(999.0);
        assertThat(result).hasSize(2);
    }

    @Test
    void reorderItem_singleItem_usesInitialPositionIfZero() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 0.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(10L))
                .thenReturn(List.of(item));

        shoppingItemService.reorderItem(1L, 1L, null, null);

        assertThat(item.getPosition()).isEqualTo(1000.0);
    }

    @Test
    void reorderItem_singleItem_keepsExistingPosition() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 555.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(10L))
                .thenReturn(List.of(item));

        shoppingItemService.reorderItem(1L, 1L, null, null);

        assertThat(item.getPosition()).isEqualTo(555.0);
    }

    @Test
    void reorderItem_whenGapTooSmall_renormalizesGroup() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1500.0);
        ShoppingItem prev = createItem(2L, "Latte", group, true, 1000.0000001);
        ShoppingItem next = createItem(3L, "Pasta", group, true, 1000.0);
        List<ShoppingItem> groupItems = List.of(item, prev, next);

        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(shoppingItemRepository.findById(2L)).thenReturn(Optional.of(prev));
        when(shoppingItemRepository.findById(3L)).thenReturn(Optional.of(next));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // Prima chiamata: rinormalizzazione; seconda: risposta finale.
        when(shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(10L))
                .thenReturn(groupItems, groupItems);

        shoppingItemService.reorderItem(1L, 1L, 2L, 3L);

        verify(shoppingItemRepository).saveAll(groupItems);
        // Dopo rinormalizzazione: next=1000, prev=2000, media=1500.
        assertThat(item.getPosition()).isEqualTo(1500.0);
    }

    @Test
    void reorderItem_alreadyBought_throwsIllegalArgument() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, false, 1000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shoppingItemService.reorderItem(1L, 1L, null, null));
        assertThat(ex.getMessage()).isEqualTo("Non è possibile riordinare un articolo già acquistato");
    }

    @Test
    void reorderItem_neighborAlreadyBought_throwsIllegalArgument() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        ShoppingItem neighbor = createItem(2L, "Latte", group, false, 2000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(shoppingItemRepository.findById(2L)).thenReturn(Optional.of(neighbor));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shoppingItemService.reorderItem(1L, 1L, 2L, null));
        assertThat(ex.getMessage()).isEqualTo("I vicini devono essere articoli da acquistare");
    }

    @Test
    void reorderItem_neighborDifferentGroup_throwsIllegalArgument() {
        Group group = createGroup(10L);
        Group otherGroup = createGroup(20L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        ShoppingItem neighbor = createItem(2L, "Latte", otherGroup, true, 2000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(shoppingItemRepository.findById(2L)).thenReturn(Optional.of(neighbor));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> shoppingItemService.reorderItem(1L, 1L, 2L, null));
        assertThat(ex.getMessage()).isEqualTo("I vicini devono appartenere allo stesso gruppo");
    }

    @Test
    void reorderItem_notFound_throwsEntityNotFound() {
        when(shoppingItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> shoppingItemService.reorderItem(99L, 1L, null, null));
    }

    @Test
    void reorderItem_nonMember_throwsAccessDenied() {
        Group group = createGroup(10L);
        ShoppingItem item = createItem(1L, "Pane", group, true, 1000.0);
        when(shoppingItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> shoppingItemService.reorderItem(1L, 1L, null, null));
    }

    private void stubReorder(ShoppingItem item, ShoppingItem prev, ShoppingItem next, List<ShoppingItem> response) {
        when(shoppingItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        if (prev != null) {
            when(shoppingItemRepository.findById(prev.getId())).thenReturn(Optional.of(prev));
        }
        if (next != null) {
            when(shoppingItemRepository.findById(next.getId())).thenReturn(Optional.of(next));
        }
        when(groupService.existsByGroupIdAndUserId(item.getGroup().getId(), 1L)).thenReturn(true);
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shoppingItemRepository.findByGroupIdOrderByToBuyDescPositionDescIdAsc(item.getGroup().getId()))
                .thenReturn(response);
    }

    private Group createGroup(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setName("group" + id);
        return group;
    }

    private ShoppingItem createItem(Long id, String name, Group group, boolean toBuy, double position) {
        ShoppingItem item = new ShoppingItem();
        item.setId(id);
        item.setName(name);
        item.setToBuy(toBuy);
        item.setPosition(position);
        item.setGroup(group);
        return item;
    }
}
