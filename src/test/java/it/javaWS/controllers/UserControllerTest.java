package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.dto.UpdateUserRequest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserControllerTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void deleteUser_blocksLoginAndPreservesBills() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        Group group = createGroup("Trip");
        addMember(group, user, GroupRole.MEMBER);
        Bill bill = createBill(group, user);

        mockMvc.perform(delete("/user/delete")
                        .with(user(user)))
                .andExpect(status().isOk());

        User deletedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedUser.isEnabled()).isFalse();
        assertThat(billRepository.findById(bill.getId())).isPresent();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new it.javaWS.models.dto.AuthRequest("mario", "Password123!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUser_withWrongPassword_returnsUnauthorized() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("WrongPassword");
        request.setUsername("newUsername");

        mockMvc.perform(put("/user/update")
                        .with(user(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateUser_withDuplicateUsername_returnsConflict() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        createUser("luigi", "luigi@example.com", "Password123!");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("Password123!");
        request.setUsername("luigi");

        mockMvc.perform(put("/user/update")
                        .with(user(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUser_withValidData_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");

        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("Password123!");
        request.setUsername("newMario");
        request.setEmail("newmario@example.com");

        mockMvc.perform(put("/user/update")
                        .with(user(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void getUser_returnsCurrentUser() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");

        mockMvc.perform(get("/user/me")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()));
    }

    @Test
    void getFriends_returnsFriends() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User friend = createUser("luigi", "luigi@example.com", "Password123!");
        createFriendship(user, friend);

        mockMvc.perform(get("/user/getFriends")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userId").value(friend.getId()));
    }

    @Test
    void sendFriendshipRequest_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User target = createUser("luigi", "luigi@example.com", "Password123!");

        mockMvc.perform(post("/user/sendFriendshipRequest")
                        .with(user(user))
                        .param("name", target.getUsername())
                        .param("message", "ciao"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptFriendshipRequest_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User requester = createUser("luigi", "luigi@example.com", "Password123!");
        createPendingFriendship(requester, user);

        mockMvc.perform(put("/user/acceptFriendship")
                        .with(user(user))
                        .param("friendId", requester.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void refuseFriendshipRequest_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User requester = createUser("luigi", "luigi@example.com", "Password123!");
        createPendingFriendship(requester, user);

        mockMvc.perform(put("/user/refuseFriendship")
                        .with(user(user))
                        .param("friendId", requester.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void cancelFriendship_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User friend = createUser("luigi", "luigi@example.com", "Password123!");
        createFriendship(user, friend);

        mockMvc.perform(delete("/user/cancelFriendship")
                        .with(user(user))
                        .param("friendId", friend.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getFriendshipRequestsSent_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User target = createUser("luigi", "luigi@example.com", "Password123!");
        Friendship friendship = new Friendship();
        friendship.setUser1(user);
        friendship.setUser2(target);
        friendship.setUserToBeConfirmed(target);
        friendship.setStato(StatoAmicizia.IN_ATTESA);
        friendship.setDataRichiesta(java.time.LocalDateTime.now());
        friendship.setMessaggio("ciao");
        friendshipRepository.save(friendship);

        mockMvc.perform(get("/user/getFriendshipReqSent")
                        .with(user(user)))
                .andExpect(status().isOk());
    }

    @Test
    void getFriendshipRequestsReceived_returnsOk() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User requester = createUser("luigi", "luigi@example.com", "Password123!");
        createPendingFriendship(requester, user);

        mockMvc.perform(get("/user/getFriendshipReqReceived")
                        .with(user(user)))
                .andExpect(status().isOk());
    }

    @Test
    void getFriendshipRequestsCount_returnsCount() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User requester1 = createUser("luigi", "luigi@example.com", "Password123!");
        User requester2 = createUser("peach", "peach@example.com", "Password123!");
        createPendingFriendship(requester1, user);
        createPendingFriendship(requester2, user);

        mockMvc.perform(get("/user/friendshipRequests/count")
                        .with(user(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void getFriends_pagination_returnsPagedResults() throws Exception {
        User user = createUser("mario", "mario@example.com", "Password123!");
        User friend1 = createUser("luigi", "luigi@example.com", "Password123!");
        User friend2 = createUser("peach", "peach@example.com", "Password123!");
        User friend3 = createUser("toad", "toad@example.com", "Password123!");
        createFriendship(user, friend1);
        createFriendship(user, friend2);
        createFriendship(user, friend3);

        mockMvc.perform(get("/user/getFriends")
                        .with(user(user))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    private void createFriendship(User u1, User u2) {
        Friendship friendship = new Friendship();
        friendship.setUser1(u1.getId() < u2.getId() ? u1 : u2);
        friendship.setUser2(u1.getId() < u2.getId() ? u2 : u1);
        friendship.setUserToBeConfirmed(u2);
        friendship.setStato(StatoAmicizia.ACCETTATA);
        friendship.setDataRichiesta(java.time.LocalDateTime.now());
        friendship.setMessaggio("");
        friendshipRepository.save(friendship);
    }

    private void createPendingFriendship(User requester, User target) {
        Friendship friendship = new Friendship();
        friendship.setUser1(requester.getId() < target.getId() ? requester : target);
        friendship.setUser2(requester.getId() < target.getId() ? target : requester);
        friendship.setUserToBeConfirmed(target);
        friendship.setStato(StatoAmicizia.IN_ATTESA);
        friendship.setDataRichiesta(java.time.LocalDateTime.now());
        friendship.setMessaggio("ciao");
        friendshipRepository.save(friendship);
    }

    private User createUser(String username, String email, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
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

    private Bill createBill(Group group, User buyer) {
        Bill bill = new Bill();
        bill.setDescription("Test bill");
        bill.setAmount(new java.math.BigDecimal("100"));
        bill.setDate(LocalDate.now());
        bill.setNotes("");
        bill.setBuyer(buyer);
        bill.setGroup(group);
        bill = billRepository.save(bill);

        Transaction transaction = new Transaction();
        transaction.setUser(buyer);
        transaction.setBill(bill);
        transaction.setGroup(group);
        transaction.setAmount(new java.math.BigDecimal("100"));
        transactionRepository.save(transaction);

        return bill;
    }
}
