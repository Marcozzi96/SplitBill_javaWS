package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Payment;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.FriendshipRepository;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.PaymentRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.services.BalanceService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentControllerTest {

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
    private FriendshipRepository friendshipRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BalanceService balanceService;

    @Test
    void createPayment_validGroupPayment_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        addFriendship(buyer, debtor);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(post("/payments")
                        .with(user(debtor))
                        .param("payeeId", buyer.getId().toString())
                        .param("amount", "40")
                        .param("groupId", group.getId().toString())
                        .param("notes", "rimborso parziale"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.amount").value(40))
                .andExpect(jsonPath("$.payer.userId").value(debtor.getId()))
                .andExpect(jsonPath("$.payee.userId").value(buyer.getId()));
    }

    @Test
    void createPayment_exceedsDebt_returnsConflict() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        addFriendship(buyer, debtor);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(post("/payments")
                        .with(user(debtor))
                        .param("payeeId", buyer.getId().toString())
                        .param("amount", "150")
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void createPayment_notMember_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        addFriendship(buyer, debtor);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(post("/payments")
                        .with(user(outsider))
                        .param("payeeId", buyer.getId().toString())
                        .param("amount", "10")
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getPayments_returnsPayments() throws Exception {
        User payer = createUser("payer", "payer@example.com");
        User payee = createUser("payee", "payee@example.com");
        createPayment(payer, payee, new BigDecimal("25"));

        mockMvc.perform(get("/payments")
                        .with(user(payer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(25));
    }

    @Test
    void createPayment_updatesBalancesAndSettlements() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        addFriendship(buyer, debtor);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(post("/payments")
                        .with(user(debtor))
                        .param("payeeId", buyer.getId().toString())
                        .param("amount", "40")
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/groups/{groupId}/settlements", group.getId())
                        .with(user(debtor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(60));

        mockMvc.perform(get("/groups/{groupId}/balance", group.getId())
                        .with(user(debtor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOwed").value(60));
    }

    @Test
    void forgiveDebt_deletedPayer_debtForgivenAndZeroed() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User ghost = createUser("ghost", "ghost@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, ghost, GroupRole.MEMBER);
        addFriendship(buyer, ghost);
        createBillWithBalance(group, buyer, ghost, new BigDecimal("100"));
        ghost.setDeleted(true);
        userRepository.save(ghost);

        mockMvc.perform(post("/payments/forgive")
                        .with(user(buyer))
                        .param("payerId", ghost.getId().toString())
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(100))
                .andExpect(jsonPath("$.notes").value("Debito dimenticato (utente eliminato)"))
                .andExpect(jsonPath("$.payer.userId").value(ghost.getId()))
                .andExpect(jsonPath("$.payee.userId").value(buyer.getId()));

        // Il debito risulta azzerato dopo il rimborso fittizio.
        assertThat(balanceService.getDebtBetween(ghost.getId(), buyer.getId(), group.getId()))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void forgiveDebt_payerNotDeleted_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        addFriendship(buyer, debtor);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(post("/payments/forgive")
                        .with(user(buyer))
                        .param("payerId", debtor.getId().toString())
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgiveDebt_noDebt_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User ghost = createUser("ghost", "ghost@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, ghost, GroupRole.MEMBER);
        ghost.setDeleted(true);
        userRepository.save(ghost);

        mockMvc.perform(post("/payments/forgive")
                        .with(user(buyer))
                        .param("payerId", ghost.getId().toString())
                        .param("groupId", group.getId().toString()))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("password");
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }

    private Group createGroup(String name) {
        Group group = new Group();
        group.setName(name);
        group.setDescription("Test group");
        group.setCreationDate(LocalDate.now());
        return groupRepository.save(group);
    }

    private void addMember(Group group, User user, GroupRole role) {
        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setDataIngresso(LocalDate.now());
        userGroup.setRole(role);
        userGroupRepository.save(userGroup);
    }

    private void addFriendship(User user1, User user2) {
        Friendship friendship = new Friendship();
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setUserToBeConfirmed(user2);
        friendship.setStato(StatoAmicizia.ACCETTATA);
        friendship.setDataRichiesta(java.time.LocalDateTime.now());
        friendship.setMessaggio("");
        friendshipRepository.save(friendship);
    }

    private Bill createBill(Group group, User buyer, User debtor, BigDecimal amount) {
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
        debtorTransaction.setAmount(amount.negate());
        transactionRepository.save(debtorTransaction);

        return bill;
    }

    private void createBillWithBalance(Group group, User buyer, User debtor, BigDecimal amount) {
        Bill bill = createBill(group, buyer, debtor, amount);
        balanceService.applyBill(bill);
    }

    private void createPayment(User payer, User payee, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setPayer(payer);
        payment.setPayee(payee);
        payment.setAmount(amount);
        payment.setDate(LocalDate.now());
        paymentRepository.save(payment);
    }
}
