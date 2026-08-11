package it.javaWS.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    void deleteBill_asOtherMember_returnsUnauthorized() throws Exception {
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

        balanceService.applyBill(bill);

        return bill;
    }
}
