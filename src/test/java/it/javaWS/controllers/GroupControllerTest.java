package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;

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
import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.FriendshipRepository;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.services.BalanceService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class GroupControllerTest {

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
    private BalanceService balanceService;

    @Test
    void getGroupMembers_asMember_returnsOk() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups/{groupId}/members", group.getId())
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(member.getId()));
    }

    @Test
    void getGroupMembers_asNonMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups/{groupId}/members", group.getId())
                        .with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getGroupMembers_showsRelazioneAmicizia() throws Exception {
        User sender = createUser("sender", "sender@example.com");
        User recipient = createUser("recipient", "recipient@example.com");
        Group group = createGroup("Trip");
        addMember(group, sender, GroupRole.ADMIN);
        addMember(group, recipient, GroupRole.MEMBER);

        // Senza alcuna amicizia la relazione è NESSUNA
        mockMvc.perform(get("/groups/{groupId}/members", group.getId())
                        .with(user(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == %d)].relazione".formatted(recipient.getId()),
                        hasItem("NESSUNA")));

        mockMvc.perform(post("/user/sendFriendshipRequest")
                        .with(user(sender))
                        .param("name", recipient.getUsername())
                        .param("message", "Ciao"))
                .andExpect(status().isOk());

        // Per il mittente la richiesta risulta inviata
        mockMvc.perform(get("/groups/{groupId}/members", group.getId())
                        .with(user(sender)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == %d)].relazione".formatted(recipient.getId()),
                        hasItem("RICHIESTA_INVIATA")));

        // Per il destinatario la richiesta risulta ricevuta
        mockMvc.perform(get("/groups/{groupId}/members", group.getId())
                        .with(user(recipient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.userId == %d)].relazione".formatted(sender.getId()),
                        hasItem("RICHIESTA_RICEVUTA")));
    }

    @Test
    void updateGroup_asAdmin_returnsOk() throws Exception {
        User admin = createUser("admin", "admin@example.com");
        Group group = createGroup("Trip");
        addMember(group, admin, GroupRole.ADMIN);

        mockMvc.perform(put("/groups/{groupId}", group.getId())
                        .with(user(admin))
                        .param("name", "New Trip Name")
                        .param("description", "Updated description"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Trip Name"));
    }

    @Test
    void updateGroup_asMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(put("/groups/{groupId}", group.getId())
                        .with(user(member))
                        .param("name", "New Trip Name"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSettlementStatus_returnsPendingDebts() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(get("/groups/{groupId}/settlement-status", group.getId())
                        .with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].debtor.userId").value(debtor.getId()))
                .andExpect(jsonPath("$[0].creditor.userId").value(buyer.getId()))
                .andExpect(jsonPath("$[0].amount").value(100));
    }

    @Test
    void deleteGroup_asAdminWithoutForce_withPendingDebts_returnsConflict() throws Exception {
        User admin = createUser("admin", "admin@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, admin, GroupRole.ADMIN);
        addMember(group, debtor, GroupRole.MEMBER);
        createBillWithBalance(group, admin, debtor, new BigDecimal("100"));

        mockMvc.perform(delete("/groups/{groupId}", group.getId())
                        .with(user(admin)))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteGroup_asAdminWithForce_returnsOk() throws Exception {
        User admin = createUser("admin", "admin@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, admin, GroupRole.ADMIN);
        addMember(group, debtor, GroupRole.MEMBER);
        createBill(group, admin, debtor, new BigDecimal("100"));

        mockMvc.perform(delete("/groups/{groupId}", group.getId())
                        .with(user(admin))
                        .param("force", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteGroup_asMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(delete("/groups/{groupId}", group.getId())
                        .with(user(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyGroupBalance_asMember_returnsBalance() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(get("/groups/{groupId}/balance", group.getId())
                        .with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value(100))
                .andExpect(jsonPath("$.totalOwed").value(0))
                .andExpect(jsonPath("$.netBalance").value(100));

        mockMvc.perform(get("/groups/{groupId}/balance", group.getId())
                        .with(user(debtor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPaid").value(0))
                .andExpect(jsonPath("$.totalOwed").value(100))
                .andExpect(jsonPath("$.netBalance").value(-100));
    }

    @Test
    void getMyGroupBalance_asNonMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups/{groupId}/balance", group.getId())
                        .with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyGroupSettlements_asMember_returnsSettlements() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        createBillWithBalance(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(get("/groups/{groupId}/settlements", group.getId())
                        .with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].counterparty.userId").value(debtor.getId()))
                .andExpect(jsonPath("$[0].amount").value(100))
                .andExpect(jsonPath("$[0].direction").value("CREDIT"));
    }

    @Test
    void getMyGroupSettlements_asNonMember_returnsForbidden() throws Exception {
        User member = createUser("member", "member@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups/{groupId}/settlements", group.getId())
                        .with(user(outsider)))
                .andExpect(status().isForbidden());
    }

    @Test
    void leaveGroup_lastMember_deletesGroup() throws Exception {
        User admin = createUser("admin", "admin@example.com");
        Group group = createGroup("Trip");
        addMember(group, admin, GroupRole.ADMIN);

        mockMvc.perform(delete("/groups/leave/{groupId}", group.getId())
                        .with(user(admin)))
                .andExpect(status().isOk());

        assertThat(groupRepository.findById(group.getId())).isEmpty();
    }

    @Test
    void createGroup_asFriends_returnsOk() throws Exception {
        User admin = createUser("admin", "admin@example.com");
        User friend = createUser("friend", "friend@example.com");
        addFriendship(admin, friend);

        mockMvc.perform(post("/groups/create")
                        .with(user(admin))
                        .param("name", "Trip")
                        .param("description", "Test trip")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("[" + friend.getId() + "]"))
                .andExpect(status().isOk());
    }

    @Test
    void createGroup_withNonFriend_returnsBadRequest() throws Exception {
        User admin = createUser("admin", "admin@example.com");
        User stranger = createUser("stranger", "stranger@example.com");

        mockMvc.perform(post("/groups/create")
                        .with(user(admin))
                        .param("name", "Trip")
                        .param("description", "Test trip")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("[" + stranger.getId() + "]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getGroup_asMember_returnsOk() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups/{groupId}", group.getId())
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(group.getId()));
    }

    @Test
    void getGroupsByUser_returnsGroups() throws Exception {
        User member = createUser("member", "member@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups")
                        .with(user(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].groupId").value(group.getId()));
    }

    @Test
    void getGroupsByUser_pagination_returnsPagedResults() throws Exception {
        User member = createUser("member", "member@example.com");
        addMember(createGroup("Trip 1"), member, GroupRole.MEMBER);
        addMember(createGroup("Trip 2"), member, GroupRole.MEMBER);
        addMember(createGroup("Trip 3"), member, GroupRole.MEMBER);

        mockMvc.perform(get("/groups")
                        .with(user(member))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void addUsersToGroup_asMemberWithFriends_returnsOk() throws Exception {
        User member = createUser("member", "member@example.com");
        User friend = createUser("friend", "friend@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);
        addFriendship(member, friend);

        mockMvc.perform(post("/groups/addUsers/{groupId}", group.getId())
                        .with(user(member))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("[" + friend.getId() + "]"))
                .andExpect(status().isOk());
    }

    @Test
    void addUsersToGroup_withNonFriend_returnsBadRequest() throws Exception {
        User member = createUser("member", "member@example.com");
        User stranger = createUser("stranger", "stranger@example.com");
        Group group = createGroup("Trip");
        addMember(group, member, GroupRole.MEMBER);

        mockMvc.perform(post("/groups/addUsers/{groupId}", group.getId())
                        .with(user(member))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("[" + stranger.getId() + "]"))
                .andExpect(status().isBadRequest());
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
}
