package it.javaWS.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.services.BalanceService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BalanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BalanceService balanceService;

    @Test
    void getUserBalance_ownBalance_returnsOk() throws Exception {
        User user = createUser("owner", "owner@example.com");
        createBalanceData(user);

        mockMvc.perform(get("/balance/{userId}", user.getId())
                        .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()));
    }

    @Test
    void getUserBalance_otherBalance_returnsUnauthorized() throws Exception {
        User owner = createUser("owner", "owner@example.com");
        User other = createUser("other", "other@example.com");
        createBalanceData(owner);

        mockMvc.perform(get("/balance/{userId}", owner.getId())
                        .with(user(other)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMyBalance_returnsDetailedBalance() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        createBillWithBalance(buyer, debtor, new BigDecimal("100"), new BigDecimal("40"));

        mockMvc.perform(get("/balance/me")
                        .with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(buyer.getId()))
                .andExpect(jsonPath("$.totalPaid").value(100))
                .andExpect(jsonPath("$.totalOwed").value(0))
                .andExpect(jsonPath("$.netBalance").value(100));

        mockMvc.perform(get("/balance/me")
                        .with(user(debtor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value(0))
                .andExpect(jsonPath("$.totalOwed").value(40))
                .andExpect(jsonPath("$.netBalance").value(-40));
    }

    @Test
    void getMySettlements_returnsOnlyOwnSettlements() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        createBillWithBalance(buyer, debtor, new BigDecimal("100"), new BigDecimal("40"));

        mockMvc.perform(get("/balance/settlements")
                        .with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].counterparty.userId").value(debtor.getId()))
                .andExpect(jsonPath("$[0].amount").value(40))
                .andExpect(jsonPath("$[0].direction").value("CREDIT"));

        mockMvc.perform(get("/balance/settlements")
                        .with(user(debtor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].counterparty.userId").value(buyer.getId()))
                .andExpect(jsonPath("$[0].amount").value(40))
                .andExpect(jsonPath("$[0].direction").value("DEBT"));

        mockMvc.perform(get("/balance/settlements")
                        .with(user(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getMyBalance_withBuyerAsDebtor_calculatesCorrectly() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        createBillWithBuyerDebtor(buyer, debtor, new BigDecimal("100"), new BigDecimal("30"), new BigDecimal("40"));

        mockMvc.perform(get("/balance/me")
                        .with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value(100))
                .andExpect(jsonPath("$.totalOwed").value(30))
                .andExpect(jsonPath("$.netBalance").value(70));
    }

    private void createBalanceData(User user) {
        Group group = new Group();
        group.setName("Trip");
        group.setDescription("Test group");
        group.setCreationDate(LocalDate.now());
        group = groupRepository.save(group);

        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setDataIngresso(LocalDate.now());
        userGroup.setRole(GroupRole.MEMBER);
        userGroupRepository.save(userGroup);

        Bill bill = new Bill();
        bill.setDescription("Test bill");
        bill.setAmount(new BigDecimal("100"));
        bill.setDate(LocalDate.now());
        bill.setNotes("");
        bill.setBuyer(user);
        bill.setGroup(group);
        bill = billRepository.save(bill);

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setBill(bill);
        transaction.setGroup(group);
        transaction.setAmount(new BigDecimal("100"));
        transactionRepository.save(transaction);

        balanceService.applyBill(bill);
    }

    private void createBillWithBalance(User buyer, User debtor, BigDecimal amount, BigDecimal debtorAmount) {
        Group group = new Group();
        group.setName("Trip");
        group.setDescription("Test group");
        group.setCreationDate(LocalDate.now());
        group = groupRepository.save(group);

        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);

        Bill bill = new Bill();
        bill.setDescription("Test bill");
        bill.setAmount(amount);
        bill.setDate(LocalDate.now());
        bill.setNotes("");
        bill.setBuyer(buyer);
        bill.setGroup(group);
        bill = billRepository.save(bill);

        Transaction buyerTransaction = new Transaction();
        buyerTransaction.setUser(buyer);
        buyerTransaction.setBill(bill);
        buyerTransaction.setGroup(group);
        buyerTransaction.setAmount(amount);
        transactionRepository.save(buyerTransaction);

        Transaction debtorTransaction = new Transaction();
        debtorTransaction.setUser(debtor);
        debtorTransaction.setBill(bill);
        debtorTransaction.setGroup(group);
        debtorTransaction.setAmount(debtorAmount.negate());
        transactionRepository.save(debtorTransaction);

        balanceService.applyBill(bill);
    }

    private void createBillWithBuyerDebtor(User buyer, User debtor, BigDecimal amount, BigDecimal buyerDebt,
            BigDecimal debtorDebt) {
        Group group = new Group();
        group.setName("Trip");
        group.setDescription("Test group");
        group.setCreationDate(LocalDate.now());
        group = groupRepository.save(group);

        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);

        Bill bill = new Bill();
        bill.setDescription("Test bill");
        bill.setAmount(amount);
        bill.setDate(LocalDate.now());
        bill.setNotes("");
        bill.setBuyer(buyer);
        bill.setGroup(group);
        bill = billRepository.save(bill);

        Transaction buyerTransaction = new Transaction();
        buyerTransaction.setUser(buyer);
        buyerTransaction.setBill(bill);
        buyerTransaction.setGroup(group);
        buyerTransaction.setAmount(amount.subtract(buyerDebt));
        transactionRepository.save(buyerTransaction);

        Transaction debtorTransaction = new Transaction();
        debtorTransaction.setUser(debtor);
        debtorTransaction.setBill(bill);
        debtorTransaction.setGroup(group);
        debtorTransaction.setAmount(debtorDebt.negate());
        transactionRepository.save(debtorTransaction);

        balanceService.applyBill(bill);
    }

    private void addMember(Group group, User user, GroupRole role) {
        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setDataIngresso(LocalDate.now());
        userGroup.setRole(role);
        userGroupRepository.save(userGroup);
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("password");
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }
}
