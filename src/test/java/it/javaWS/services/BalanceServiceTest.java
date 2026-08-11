package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.javaWS.models.dto.UserBalanceDTO;
import it.javaWS.models.dto.UserSettlementDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.PairwiseSettlement;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserBalance;
import it.javaWS.models.entities.UserGroupBalance;
import it.javaWS.models.enums.SettlementDirection;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.PairwiseSettlementRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserBalanceRepository;
import it.javaWS.repositories.UserGroupBalanceRepository;
import it.javaWS.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private UserBalanceRepository userBalanceRepository;

    @Mock
    private UserGroupBalanceRepository userGroupBalanceRepository;

    @Mock
    private PairwiseSettlementRepository pairwiseSettlementRepository;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void getDetailedBalance_existingBalance_returnsDto() {
        User user = createUser(1L, "alice");
        UserBalance balance = new UserBalance();
        balance.setUser(user);
        balance.setTotalPaid(new BigDecimal("200"));
        balance.setTotalOwed(new BigDecimal("50"));
        balance.setNetBalance(new BigDecimal("150"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBalanceRepository.findByUserId(1L)).thenReturn(Optional.of(balance));

        UserBalanceDTO dto = balanceService.getDetailedBalance(1L);

        assertThat(dto.getUserId()).isEqualTo(1L);
        assertThat(dto.getTotalPaid()).isEqualByComparingTo("200");
        assertThat(dto.getTotalOwed()).isEqualByComparingTo("50");
        assertThat(dto.getNetBalance()).isEqualByComparingTo("150");
    }

    @Test
    void getDetailedBalance_missingBalance_returnsZeros() {
        User user = createUser(1L, "alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBalanceRepository.findByUserId(1L)).thenReturn(Optional.empty());

        UserBalanceDTO dto = balanceService.getDetailedBalance(1L);

        assertThat(dto.getTotalPaid()).isEqualByComparingTo("0");
        assertThat(dto.getTotalOwed()).isEqualByComparingTo("0");
        assertThat(dto.getNetBalance()).isEqualByComparingTo("0");
    }

    @Test
    void getDetailedGroupBalance_existingBalance_returnsDto() {
        User user = createUser(1L, "alice");
        Group group = createGroup(10L);
        UserGroupBalance balance = new UserGroupBalance();
        balance.setUser(user);
        balance.setGroup(group);
        balance.setTotalPaid(new BigDecimal("100"));
        balance.setTotalOwed(new BigDecimal("30"));
        balance.setNetBalance(new BigDecimal("70"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(1L, 10L)).thenReturn(Optional.of(balance));

        UserBalanceDTO dto = balanceService.getDetailedGroupBalance(1L, 10L);

        assertThat(dto.getTotalPaid()).isEqualByComparingTo("100");
        assertThat(dto.getTotalOwed()).isEqualByComparingTo("30");
        assertThat(dto.getNetBalance()).isEqualByComparingTo("70");
    }

    @Test
    void getUserSettlements_aggregatesCounterpartyBalances() {
        User user = createUser(1L, "alice");
        User bob = createUser(2L, "bob");
        User carol = createUser(3L, "carol");
        Group group = createGroup(10L);

        PairwiseSettlement s1 = createSettlement(user, bob, group, new BigDecimal("40")); // alice debitore
        PairwiseSettlement s2 = createSettlement(carol, user, group, new BigDecimal("30")); // alice creditore
        PairwiseSettlement s3 = createSettlement(user, bob, createGroup(20L), new BigDecimal("10")); // altro gruppo

        when(pairwiseSettlementRepository.findByDebtorIdOrCreditorId(1L, 1L))
                .thenReturn(List.of(s1, s2, s3));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(userRepository.findById(3L)).thenReturn(Optional.of(carol));

        List<UserSettlementDTO> settlements = balanceService.getUserSettlements(1L);

        assertThat(settlements).hasSize(2);
        assertThat(settlements).anyMatch(s -> s.getCounterparty().getUserId().equals(2L)
                && s.getAmount().compareTo(new BigDecimal("50")) == 0
                && s.getDirection() == SettlementDirection.DEBT);
        assertThat(settlements).anyMatch(s -> s.getCounterparty().getUserId().equals(3L)
                && s.getAmount().compareTo(new BigDecimal("30")) == 0
                && s.getDirection() == SettlementDirection.CREDIT);
    }

    @Test
    void getUserGroupSettlements_filtersByGroup() {
        User user = createUser(1L, "alice");
        User bob = createUser(2L, "bob");
        Group group = createGroup(10L);

        PairwiseSettlement s1 = createSettlement(user, bob, group, new BigDecimal("40"));
        PairwiseSettlement s2 = createSettlement(user, bob, createGroup(20L), new BigDecimal("10"));

        when(pairwiseSettlementRepository.findByDebtorIdAndGroupId(1L, 10L)).thenReturn(List.of(s1));
        when(pairwiseSettlementRepository.findByCreditorIdAndGroupId(1L, 10L)).thenReturn(List.of());
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        List<UserSettlementDTO> settlements = balanceService.getUserGroupSettlements(1L, 10L);

        assertThat(settlements).hasSize(1);
        assertThat(settlements.getFirst().getAmount()).isEqualByComparingTo("40");
    }

    @Test
    void applyBill_buyerAndDebtors_updatesBalancesAndSettlements() {
        User buyer = createUser(1L, "alice");
        User bob = createUser(2L, "bob");
        Group group = createGroup(10L);
        Bill bill = createBill(100L, buyer, group, new BigDecimal("100"));
        Transaction buyerTransaction = createTransaction(1L, buyer, bill, group, new BigDecimal("70"));
        Transaction bobTransaction = createTransaction(2L, bob, bill, group, new BigDecimal("-40"));
        bill.setTransactions(List.of(buyerTransaction, bobTransaction));

        UserBalance buyerBalance = new UserBalance();
        buyerBalance.setUser(buyer);
        UserGroupBalance buyerGroupBalance = new UserGroupBalance();
        buyerGroupBalance.setUser(buyer);
        buyerGroupBalance.setGroup(group);

        UserBalance bobBalance = new UserBalance();
        bobBalance.setUser(bob);
        UserGroupBalance bobGroupBalance = new UserGroupBalance();
        bobGroupBalance.setUser(bob);
        bobGroupBalance.setGroup(group);

        when(transactionRepository.findByBill_Id(100L)).thenReturn(bill.getTransactions());
        when(userBalanceRepository.findByUserId(1L)).thenReturn(Optional.of(buyerBalance));
        when(userBalanceRepository.findByUserId(2L)).thenReturn(Optional.of(bobBalance));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(1L, 10L)).thenReturn(Optional.of(buyerGroupBalance));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(2L, 10L)).thenReturn(Optional.of(bobGroupBalance));
        when(pairwiseSettlementRepository.findByDebtorIdAndCreditorIdAndGroupId(2L, 1L, 10L))
                .thenReturn(Optional.empty());
        when(pairwiseSettlementRepository.findByDebtorIdAndCreditorIdAndGroupId(1L, 2L, 10L))
                .thenReturn(Optional.empty());

        balanceService.applyBill(bill);

        assertThat(buyerBalance.getTotalPaid()).isEqualByComparingTo("100");
        assertThat(buyerBalance.getTotalOwed()).isEqualByComparingTo("30"); // debito implicito buyer
        assertThat(buyerBalance.getNetBalance()).isEqualByComparingTo("70");
        assertThat(bobBalance.getTotalOwed()).isEqualByComparingTo("40");
        assertThat(bobBalance.getNetBalance()).isEqualByComparingTo("-40");

        assertThat(buyerGroupBalance.getTotalPaid()).isEqualByComparingTo("100");
        assertThat(buyerGroupBalance.getTotalOwed()).isEqualByComparingTo("30");
        assertThat(bobGroupBalance.getTotalOwed()).isEqualByComparingTo("40");

        ArgumentCaptor<PairwiseSettlement> captor = ArgumentCaptor.forClass(PairwiseSettlement.class);
        verify(pairwiseSettlementRepository).save(captor.capture());
        PairwiseSettlement saved = captor.getValue();
        assertThat(saved.getDebtor().getId()).isEqualTo(2L);
        assertThat(saved.getCreditor().getId()).isEqualTo(1L);
        assertThat(saved.getAmount()).isEqualByComparingTo("40");
    }

    @Test
    void applyBill_withExistingInverseSettlement_compensates() {
        User buyer = createUser(1L, "alice");
        User bob = createUser(2L, "bob");
        Group group = createGroup(10L);
        Bill bill = createBill(100L, buyer, group, new BigDecimal("100"));
        Transaction buyerTransaction = createTransaction(1L, buyer, bill, group, new BigDecimal("100"));
        Transaction bobTransaction = createTransaction(2L, bob, bill, group, new BigDecimal("-40"));
        bill.setTransactions(List.of(buyerTransaction, bobTransaction));

        UserBalance buyerBalance = new UserBalance();
        buyerBalance.setUser(buyer);
        UserGroupBalance buyerGroupBalance = new UserGroupBalance();
        buyerGroupBalance.setUser(buyer);
        buyerGroupBalance.setGroup(group);

        UserBalance bobBalance = new UserBalance();
        bobBalance.setUser(bob);
        UserGroupBalance bobGroupBalance = new UserGroupBalance();
        bobGroupBalance.setUser(bob);
        bobGroupBalance.setGroup(group);

        PairwiseSettlement inverse = new PairwiseSettlement();
        inverse.setDebtor(buyer);
        inverse.setCreditor(bob);
        inverse.setGroup(group);
        inverse.setAmount(new BigDecimal("60"));

        when(transactionRepository.findByBill_Id(100L)).thenReturn(bill.getTransactions());
        when(userBalanceRepository.findByUserId(1L)).thenReturn(Optional.of(buyerBalance));
        when(userBalanceRepository.findByUserId(2L)).thenReturn(Optional.of(bobBalance));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(1L, 10L)).thenReturn(Optional.of(buyerGroupBalance));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(2L, 10L)).thenReturn(Optional.of(bobGroupBalance));
        when(pairwiseSettlementRepository.findByDebtorIdAndCreditorIdAndGroupId(1L, 2L, 10L))
                .thenReturn(Optional.of(inverse));

        balanceService.applyBill(bill);

        assertThat(inverse.getAmount()).isEqualByComparingTo("20"); // 60 - 40
        verify(pairwiseSettlementRepository, never()).save(any(PairwiseSettlement.class));
    }

    @Test
    void revertBill_restoresBalancesAndSettlements() {
        User buyer = createUser(1L, "alice");
        User bob = createUser(2L, "bob");
        Group group = createGroup(10L);
        Bill bill = createBill(100L, buyer, group, new BigDecimal("100"));
        Transaction buyerTransaction = createTransaction(1L, buyer, bill, group, new BigDecimal("100"));
        Transaction bobTransaction = createTransaction(2L, bob, bill, group, new BigDecimal("-40"));
        bill.setTransactions(List.of(buyerTransaction, bobTransaction));

        UserBalance buyerBalance = new UserBalance();
        buyerBalance.setUser(buyer);
        buyerBalance.setTotalPaid(new BigDecimal("100"));
        buyerBalance.setTotalOwed(BigDecimal.ZERO);
        UserGroupBalance buyerGroupBalance = new UserGroupBalance();
        buyerGroupBalance.setUser(buyer);
        buyerGroupBalance.setGroup(group);
        buyerGroupBalance.setTotalPaid(new BigDecimal("100"));

        UserBalance bobBalance = new UserBalance();
        bobBalance.setUser(bob);
        bobBalance.setTotalOwed(new BigDecimal("40"));
        UserGroupBalance bobGroupBalance = new UserGroupBalance();
        bobGroupBalance.setUser(bob);
        bobGroupBalance.setGroup(group);
        bobGroupBalance.setTotalOwed(new BigDecimal("40"));

        PairwiseSettlement direct = new PairwiseSettlement();
        direct.setDebtor(bob);
        direct.setCreditor(buyer);
        direct.setGroup(group);
        direct.setAmount(new BigDecimal("40"));

        when(transactionRepository.findByBill_Id(100L)).thenReturn(bill.getTransactions());
        when(userBalanceRepository.findByUserId(1L)).thenReturn(Optional.of(buyerBalance));
        when(userBalanceRepository.findByUserId(2L)).thenReturn(Optional.of(bobBalance));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(1L, 10L)).thenReturn(Optional.of(buyerGroupBalance));
        when(userGroupBalanceRepository.findByUserIdAndGroupId(2L, 10L)).thenReturn(Optional.of(bobGroupBalance));
        when(pairwiseSettlementRepository.findByDebtorIdAndCreditorIdAndGroupId(2L, 1L, 10L))
                .thenReturn(Optional.of(direct));

        balanceService.revertBill(bill);

        assertThat(buyerBalance.getTotalPaid()).isEqualByComparingTo("0");
        assertThat(bobBalance.getTotalOwed()).isEqualByComparingTo("0");
        verify(pairwiseSettlementRepository).delete(direct);
    }

    @Test
    void revertGroupBalances_restoresGlobalBalancesAndDeletesGroupData() {
        User user = createUser(1L, "alice");
        Group group = createGroup(10L);

        UserBalance userBalance = new UserBalance();
        userBalance.setUser(user);
        userBalance.setTotalPaid(new BigDecimal("100"));
        userBalance.setTotalOwed(new BigDecimal("30"));
        userBalance.setNetBalance(new BigDecimal("70"));

        UserGroupBalance groupBalance = new UserGroupBalance();
        groupBalance.setUser(user);
        groupBalance.setGroup(group);
        groupBalance.setTotalPaid(new BigDecimal("100"));
        groupBalance.setTotalOwed(new BigDecimal("30"));
        groupBalance.setNetBalance(new BigDecimal("70"));

        when(userGroupBalanceRepository.findByGroupId(10L)).thenReturn(List.of(groupBalance));
        when(userBalanceRepository.findByUserId(1L)).thenReturn(Optional.of(userBalance));

        balanceService.revertGroupBalances(group);

        assertThat(userBalance.getTotalPaid()).isEqualByComparingTo("0");
        assertThat(userBalance.getTotalOwed()).isEqualByComparingTo("0");
        assertThat(userBalance.getNetBalance()).isEqualByComparingTo("0");
        verify(userGroupBalanceRepository).deleteByGroupId(10L);
        verify(pairwiseSettlementRepository).deleteByGroupId(10L);
    }

    private User createUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPassword("password");
        return user;
    }

    private Group createGroup(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setName("group" + id);
        return group;
    }

    private Bill createBill(Long id, User buyer, Group group, BigDecimal amount) {
        Bill bill = new Bill();
        bill.setId(id);
        bill.setBuyer(buyer);
        bill.setGroup(group);
        bill.setAmount(amount);
        return bill;
    }

    private Transaction createTransaction(Long id, User user, Bill bill, Group group, BigDecimal amount) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setUser(user);
        transaction.setBill(bill);
        transaction.setGroup(group);
        transaction.setAmount(amount);
        return transaction;
    }

    private PairwiseSettlement createSettlement(User debtor, User creditor, Group group, BigDecimal amount) {
        PairwiseSettlement settlement = new PairwiseSettlement();
        settlement.setDebtor(debtor);
        settlement.setCreditor(creditor);
        settlement.setGroup(group);
        settlement.setAmount(amount);
        return settlement;
    }
}
