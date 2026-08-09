package it.javaWS.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionControllerTest {

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

    @Test
    void deleteTransaction_asBuyer_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        Transaction transaction = createTransaction(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(delete("/transactions/{id}", transaction.getId())
                        .with(user(buyer)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteTransaction_asAdmin_returnsOk() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User admin = createUser("admin", "admin@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, admin, GroupRole.ADMIN);
        addMember(group, debtor, GroupRole.MEMBER);
        Transaction transaction = createTransaction(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(delete("/transactions/{id}", transaction.getId())
                        .with(user(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteTransaction_asOtherMember_returnsUnauthorized() throws Exception {
        User buyer = createUser("buyer", "buyer@example.com");
        User member = createUser("member", "member@example.com");
        User debtor = createUser("debtor", "debtor@example.com");
        Group group = createGroup("Trip");
        addMember(group, buyer, GroupRole.MEMBER);
        addMember(group, member, GroupRole.MEMBER);
        addMember(group, debtor, GroupRole.MEMBER);
        Transaction transaction = createTransaction(group, buyer, debtor, new BigDecimal("100"));

        mockMvc.perform(delete("/transactions/{id}", transaction.getId())
                        .with(user(member)))
                .andExpect(status().isUnauthorized());
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

    private Transaction createTransaction(Group group, User buyer, User debtor, BigDecimal amount) {
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
        return transactionRepository.save(debtorTransaction);
    }
}
