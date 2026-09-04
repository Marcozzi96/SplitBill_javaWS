package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.ShoppingItem;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.ShoppingItemRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.InvalidBillException;

@ExtendWith(MockitoExtension.class)
class BillServiceTest {

    @Mock
    private BillRepository billRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BalanceService balanceService;

    @Mock
    private GroupService groupService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private ShoppingItemRepository shoppingItemRepository;

    @InjectMocks
    private BillService billService;

    private final AtomicLong transactionIdGenerator = new AtomicLong(1);

    private void mockRepositories() {
        when(billRepository.save(any(Bill.class))).thenAnswer(invocation -> {
            Bill bill = invocation.getArgument(0);
            bill.setId(100L);
            return bill;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(transactionIdGenerator.getAndIncrement());
            return transaction;
        });
    }

    @Test
    void createBill_buyerNotDebtor_success() {
        mockRepositories();

        User buyer = createUser(1L);
        User b = createUser(2L);
        User c = createUser(3L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                b, new BigDecimal("40"),
                c, new BigDecimal("60"));

        Bill bill = billService.createBill("Cena", new BigDecimal("100"), "note", buyer, group, debits, List.of());

        assertThat(bill.getTransactions()).hasSize(3);
        assertThat(bill.getTransactions())
                .anyMatch(t -> t.getUser().getId().equals(1L) && t.getAmount().compareTo(new BigDecimal("100")) == 0)
                .anyMatch(t -> t.getUser().getId().equals(2L) && t.getAmount().compareTo(new BigDecimal("-40")) == 0)
                .anyMatch(t -> t.getUser().getId().equals(3L) && t.getAmount().compareTo(new BigDecimal("-60")) == 0);
        verify(balanceService).applyBill(bill);
    }

    @Test
    void createBill_buyerIsDebtor_success() {
        mockRepositories();

        User buyer = createUser(1L);
        User b = createUser(2L);
        User c = createUser(3L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                buyer, new BigDecimal("30"),
                b, new BigDecimal("40"),
                c, new BigDecimal("30"));

        Bill bill = billService.createBill("Cena", new BigDecimal("100"), "note", buyer, group, debits, List.of());

        assertThat(bill.getTransactions()).hasSize(3);
        assertThat(bill.getTransactions())
                .anyMatch(t -> t.getUser().getId().equals(1L) && t.getAmount().compareTo(new BigDecimal("70")) == 0)
                .anyMatch(t -> t.getUser().getId().equals(2L) && t.getAmount().compareTo(new BigDecimal("-40")) == 0)
                .anyMatch(t -> t.getUser().getId().equals(3L) && t.getAmount().compareTo(new BigDecimal("-30")) == 0);
        verify(balanceService).applyBill(bill);
    }

    @Test
    void createBill_sumGreaterThanAmount_throwsInvalidBillException() {
        User buyer = createUser(1L);
        User b = createUser(2L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                b, new BigDecimal("60"),
                buyer, new BigDecimal("50"));

        InvalidBillException exception = assertThrows(InvalidBillException.class,
                () -> billService.createBill("Cena", new BigDecimal("100"), "note", buyer, group, debits, List.of()));
        assertThat(exception.getMessage()).contains("non corrisponde");
    }

    @Test
    void createBill_sumLessThanAmount_throwsInvalidBillException() {
        User buyer = createUser(1L);
        User b = createUser(2L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                b, new BigDecimal("30"));

        InvalidBillException exception = assertThrows(InvalidBillException.class,
                () -> billService.createBill("Cena", new BigDecimal("100"), "note", buyer, group, debits, List.of()));
        assertThat(exception.getMessage()).contains("non corrisponde");
    }

    @Test
    void createBill_negativeAmount_throwsInvalidBillException() {
        User buyer = createUser(1L);
        User b = createUser(2L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                b, new BigDecimal("50"),
                buyer, new BigDecimal("50"));

        InvalidBillException exception = assertThrows(InvalidBillException.class,
                () -> billService.createBill("Cena", new BigDecimal("-10"), "note", buyer, group, debits, List.of()));
        assertThat(exception.getMessage()).contains("positivo");
    }

    @Test
    void createBill_withShoppingItems_marksPurchasedAndSnapshots() {
        mockRepositories();
        when(shoppingItemRepository.save(any(ShoppingItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User buyer = createUser(1L);
        User b = createUser(2L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                b, new BigDecimal("50"),
                buyer, new BigDecimal("50"));

        ShoppingItem pane = createShoppingItem(1L, "Pane", null, group);
        ShoppingItem latte = createShoppingItem(2L, "Latte", "intero", group);

        Bill bill = billService.createBill("Spesa", new BigDecimal("100"), "note", buyer, group, debits,
                List.of(latte, pane));

        assertThat(pane.isToBuy()).isFalse();
        assertThat(latte.isToBuy()).isFalse();
        verify(shoppingItemRepository).save(pane);
        verify(shoppingItemRepository).save(latte);
        // Snapshot ordinato per id: "nome" oppure "nome (nota)".
        assertThat(bill.getPurchasedItems()).isEqualTo("Pane, Latte (intero)");
    }

    @Test
    void createBill_withoutShoppingItems_noSnapshot() {
        mockRepositories();

        User buyer = createUser(1L);
        User b = createUser(2L);
        Group group = createGroup(10L);

        Map<User, BigDecimal> debits = Map.of(
                b, new BigDecimal("50"),
                buyer, new BigDecimal("50"));

        Bill bill = billService.createBill("Cena", new BigDecimal("100"), "note", buyer, group, debits, null);

        assertThat(bill.getPurchasedItems()).isNull();
    }

    private ShoppingItem createShoppingItem(Long id, String name, String note, Group group) {
        ShoppingItem item = new ShoppingItem();
        item.setId(id);
        item.setName(name);
        item.setNote(note);
        item.setToBuy(true);
        item.setGroup(group);
        return item;
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("password");
        return user;
    }

    private Group createGroup(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setName("group" + id);
        return group;
    }
}
