package it.javaWS.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
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
class BillControllerTest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Test
    void deleteBill_asBuyer_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(buyer)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBill_asAdmin_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User admin = createUser("admin", "admin@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, admin, GroupRole.ADMIN);
        Bill bill = createBill(group, buyer, admin, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBill_asMember_returnsOk() throws Exception {
        // Qualsiasi membro attivo del gruppo può eliminare una spesa.
        User buyer = createUser("buyer", "buyer@example.com");
        User member = createUser("member", "member@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, member, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(member)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBill_asNonMember_returnsUnauthorized() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(outsider)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBill_success_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("40"),
                buyer.getId(), new BigDecimal("60"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Dinner")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("groupId", group.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk());
    }

    @Test
    void createBill_negativeAmount_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Dinner")
                        .param("amount", "-10")
                        .param("notes", "")
                        .param("groupId", group.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBill_debtorNotInGroup_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("50"),
                outsider.getId(), new BigDecimal("50"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Dinner")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("groupId", group.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBill_debitSumMismatch_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        User another = createUser("another", "another@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        addMember(group, another, GroupRole.MEMBER);

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("60"),
                another.getId(), new BigDecimal("50"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Dinner")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("groupId", group.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBill_asBuyer_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("40"),
                buyer.getId(), new BigDecimal("60"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(buyer))
                        .param("description", "Updated Dinner")
                        .param("amount", "100")
                        .param("notes", "updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated Dinner"));
    }

    @Test
    void updateBill_asAdmin_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User admin = createUser("admin", "admin@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, admin, GroupRole.ADMIN);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(admin))
                        .param("description", "Admin Updated")
                        .param("amount", "100")
                        .param("notes", "updated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Admin Updated"));
    }

    @Test
    void updateBill_asMember_returnsOk() throws Exception {
        // Qualsiasi membro attivo del gruppo può modificare una spesa.
        User buyer = createUser("buyer", "buyer@example.com");
        User member = createUser("member", "member@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, member, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(member))
                        .param("description", "Member Updated")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Member Updated"));
    }

    @Test
    void updateBill_asNonMember_returnsUnauthorized() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(outsider))
                        .param("description", "Hacked")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateBill_debitSumMismatch_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, other, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                other.getId(), new BigDecimal("60"),
                buyer.getId(), new BigDecimal("60"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(buyer))
                        .param("description", "Wrong")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBillsByGroup_pagination_returnsPagedResults() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User other = createUser("other", "other@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, other, GroupRole.MEMBER);
        createBill(group, buyer, other, new BigDecimal("10"));
        createBill(group, buyer, other, new BigDecimal("20"));
        createBill(group, buyer, other, new BigDecimal("30"));

        mockMvc.perform(get("/bills/group/{groupId}", group.getId())
                        .with(user(buyer))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void createBill_withoutGroupId_personalBillBetweenFriends_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        makeFriends(buyer, friend);

        Map<Long, BigDecimal> debits = Map.of(
                friend.getId(), new BigDecimal("40"),
                buyer.getId(), new BigDecimal("60"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Pizza")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void createBill_personalBill_debtorNotFriend_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User stranger = createUser("stranger", "stranger@example.com");

        Map<Long, BigDecimal> debits = Map.of(
                stranger.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Pizza")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBill_personalBill_debtorNotFound_returnsBadRequest() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");

        Map<Long, BigDecimal> debits = Map.of(
                9999L, new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(post("/bills/new")
                        .with(user(buyer))
                        .param("description", "Pizza")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateBill_personalBill_asBuyer_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        makeFriends(buyer, friend);
        Bill bill = createBill(null, buyer, friend, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                friend.getId(), new BigDecimal("70"),
                buyer.getId(), new BigDecimal("30"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(buyer))
                        .param("description", "Updated")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    @Test
    void updateBill_personalBill_asDebtor_returnsOk() throws Exception {
        // Sulle spese personali può modificare chiunque sia coinvolto (buyer o debitore).
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        makeFriends(buyer, friend);
        Bill bill = createBill(null, buyer, friend, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                friend.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(friend))
                        .param("description", "Updated by debtor")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated by debtor"));
    }

    @Test
    void updateBill_personalBill_asUnrelatedUser_returnsUnauthorized() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        makeFriends(buyer, friend);
        Bill bill = createBill(null, buyer, friend, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                friend.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(outsider))
                        .param("description", "Hacked")
                        .param("amount", "100")
                        .param("notes", "")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteBill_personalBill_asDebtor_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        makeFriends(buyer, friend);
        Bill bill = createBill(null, buyer, friend, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(friend)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteBill_personalBill_asUnrelatedUser_returnsUnauthorized() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        makeFriends(buyer, friend);
        Bill bill = createBill(null, buyer, friend, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(outsider)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteBill_personalBill_asBuyer_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User friend = createUser("friend", "friend@example.com");
        makeFriends(buyer, friend);
        Bill bill = createBill(null, buyer, friend, new BigDecimal("100"));

        mockMvc.perform(delete("/bills/{id}", bill.getId())
                        .with(user(buyer)))
                .andExpect(status().isOk());
    }

    @Test
    void createBill_groupBill_withBuyerId_returnsOk() throws Exception {
        User creator = createUser("creator", "creator@example.com");
        User payer = createUser("payer", "payer@example.com");
        Group group = createGroup("Trip");
        addMember(group, creator, GroupRole.MEMBER);
        addMember(group, payer, GroupRole.MEMBER);

        // Ha pagato un altro membro del gruppo.
        Map<Long, BigDecimal> debits = Map.of(
                payer.getId(), new BigDecimal("50"),
                creator.getId(), new BigDecimal("50"));

        mockMvc.perform(post("/bills/new")
                        .with(user(creator))
                        .param("description", "Dinner")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("groupId", group.getId().toString())
                        .param("buyerId", payer.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyer.userId").value(payer.getId()));
    }

    @Test
    void createBill_buyerNotInGroup_returnsBadRequest() throws Exception {
        User creator = createUser("creator", "creator@example.com");
        User outsider = createUser("outsider", "outsider@example.com");
        Group group = createGroup("Trip");
        addMember(group, creator, GroupRole.MEMBER);

        Map<Long, BigDecimal> debits = Map.of(creator.getId(), new BigDecimal("100"));

        mockMvc.perform(post("/bills/new")
                        .with(user(creator))
                        .param("description", "Dinner")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("groupId", group.getId().toString())
                        .param("buyerId", outsider.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBill_personalBill_friendIsBuyer_returnsOk() throws Exception {
        User me = createUser("me", "me@example.com");
        User friend = createUser("friend", "friend@example.com");
        makeFriends(me, friend);

        // Ha pagato l'amico: io sono l'unico debitore.
        Map<Long, BigDecimal> debits = Map.of(me.getId(), new BigDecimal("100"));

        mockMvc.perform(post("/bills/new")
                        .with(user(me))
                        .param("description", "Concerto")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("buyerId", friend.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyer.userId").value(friend.getId()))
                .andExpect(jsonPath("$.groupId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void updateBill_changeBuyer_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User payer = createUser("payer", "payer@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, payer, GroupRole.MEMBER);
        Bill bill = createBill(group, buyer, payer, new BigDecimal("100"));

        Map<Long, BigDecimal> debits = Map.of(
                payer.getId(), new BigDecimal("50"),
                buyer.getId(), new BigDecimal("50"));

        mockMvc.perform(put("/bills/{id}", bill.getId())
                        .with(user(buyer))
                        .param("description", "Test bill")
                        .param("amount", "100")
                        .param("notes", "")
                        .param("buyerId", payer.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyer.userId").value(payer.getId()));
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

    // Amicizia accettata: il vincolo DB richiede user1_id < user2_id.
    private void makeFriends(User a, User b) {
        Friendship friendship = new Friendship();
        if (a.getId() < b.getId()) {
            friendship.setUser1(a);
            friendship.setUser2(b);
        } else {
            friendship.setUser1(b);
            friendship.setUser2(a);
        }
        friendship.setUserToBeConfirmed(b);
        friendship.setMessaggio("test");
        friendship.setStato(StatoAmicizia.ACCETTATA);
        friendship.setDataRichiesta(LocalDateTime.now());
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

        balanceService.applyBill(bill);

        return bill;
    }
}
