package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.javaWS.enums.AuthTokenType;
import it.javaWS.models.dto.AuthRequest;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.AuthTokenRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.EmailUtil;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EndToEndFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @MockitoBean
    private EmailUtil emailUtil;

    @Test
    void fullFlow_registrationToPayment() throws Exception {
        String usernameA = "alice" + System.nanoTime();
        String usernameB = "bob" + System.nanoTime();
        String emailA = usernameA + "@example.com";
        String emailB = usernameB + "@example.com";
        String password = "Password123!";

        register(usernameA, emailA, password);
        register(usernameB, emailB, password);

        Long userAId = confirmEmail(usernameA, emailA, password);
        Long userBId = confirmEmail(usernameB, emailB, password);

        User userA = userRepository.findById(userAId).orElseThrow();
        User userB = userRepository.findById(userBId).orElseThrow();

        sendFriendshipRequest(userA, usernameB);
        acceptFriendship(userB, userAId);

        Long groupId = createGroup(userA, "Trip", "Test trip", Set.of(userBId));

        Map<Long, BigDecimal> debits = Map.of(userAId, new BigDecimal("50"), userBId, new BigDecimal("50"));
        createBill(userA, groupId, "Dinner", new BigDecimal("100"), debits);

        BigDecimal debtBefore = getDebtTowards(userB, groupId, userAId);
        assertThat(debtBefore).isEqualByComparingTo(new BigDecimal("50"));

        createPayment(userB, userAId, new BigDecimal("30"), groupId);

        BigDecimal debtAfter = getDebtTowards(userB, groupId, userAId);
        assertThat(debtAfter).isEqualByComparingTo(new BigDecimal("20"));
    }

    private void register(String username, String email, String password) throws Exception {
        AuthRequest request = new AuthRequest(username, password, email);
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private Long confirmEmail(String username, String email, String password) throws Exception {
        String token = authTokenRepository.findAll().stream()
                .filter(t -> t.getType() == AuthTokenType.REGISTRATION && username.equals(t.getUsername()))
                .findFirst()
                .orElseThrow()
                .getToken();
        MvcResult result = mockMvc.perform(get("/auth/confirmEmail")
                        .param("token", token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("userId").asLong();
    }

    private void sendFriendshipRequest(User user, String targetUsername) throws Exception {
        mockMvc.perform(post("/user/sendFriendshipRequest")
                        .with(user(user))
                        .param("name", targetUsername)
                        .param("message", "ciao"))
                .andExpect(status().isOk());
    }

    private void acceptFriendship(User user, Long friendId) throws Exception {
        mockMvc.perform(put("/user/acceptFriendship")
                        .with(user(user))
                        .param("friendId", friendId.toString()))
                .andExpect(status().isOk());
    }

    private Long createGroup(User user, String name, String description, Set<Long> memberIds) throws Exception {
        MvcResult result = mockMvc.perform(post("/groups/create")
                        .with(user(user))
                        .param("name", name)
                        .param("description", description)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberIds)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("groupId").asLong();
    }

    private void createBill(User user, Long groupId, String description, BigDecimal amount,
            Map<Long, BigDecimal> debits) throws Exception {
        mockMvc.perform(post("/bills/new")
                        .with(user(user))
                        .param("description", description)
                        .param("amount", amount.toPlainString())
                        .param("notes", "")
                        .param("groupId", groupId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debits)))
                .andExpect(status().isOk());
    }

    private void createPayment(User user, Long payeeId, BigDecimal amount, Long groupId) throws Exception {
        mockMvc.perform(post("/payments")
                        .with(user(user))
                        .param("payeeId", payeeId.toString())
                        .param("amount", amount.toPlainString())
                        .param("groupId", groupId.toString())
                        .param("notes", "rimborso"))
                .andExpect(status().isOk());
    }

    private BigDecimal getDebtTowards(User user, Long groupId, Long counterpartyId) throws Exception {
        MvcResult result = mockMvc.perform(get("/groups/{groupId}/settlements", groupId)
                        .with(user(user)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode array = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode settlement : array) {
            if (settlement.get("counterparty").get("userId").asLong() == counterpartyId) {
                return new BigDecimal(settlement.get("amount").asText());
            }
        }
        return BigDecimal.ZERO;
    }
}
