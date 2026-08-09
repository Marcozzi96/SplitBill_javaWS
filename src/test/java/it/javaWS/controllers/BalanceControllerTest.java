package it.javaWS.controllers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
