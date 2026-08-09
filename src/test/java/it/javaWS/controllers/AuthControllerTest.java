package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import it.javaWS.models.dto.AuthRequest;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.EmailUtil;
import it.javaWS.utils.JwtUtil;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private EmailUtil emailUtil;

    @Test
    void login_withValidCredentials_returnsToken() {
        createUser("mario", "mario@example.com", "Password123!");
        AuthRequest request = new AuthRequest("mario", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"token\"");
        assertThat(response.getBody()).contains("\"username\":\"mario\"");
    }

    @Test
    void login_withWrongPassword_returnsUnauthorized() {
        createUser("mario", "mario@example.com", "Password123!");
        AuthRequest request = new AuthRequest("mario", "WrongPassword");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_withNonExistentUser_returnsUnauthorized() {
        AuthRequest request = new AuthRequest("nonexistent", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_withNewUser_returnsConfirmationMessage() {
        AuthRequest request = new AuthRequest("luigi", "Password123!", "luigi@example.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Conferma l'email all'indirizzo luigi@example.com");
        assertThat(userRepository.findByUsernameIgnoreCase("luigi")).isEmpty();
    }

    @Test
    void register_withDuplicateUsername_returnsBadRequest() {
        createUser("mario", "mario@example.com", "Password123!");
        AuthRequest request = new AuthRequest("mario", "Password123!", "other@example.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_withDuplicateEmail_returnsBadRequest() {
        createUser("mario", "mario@example.com", "Password123!");
        AuthRequest request = new AuthRequest("other", "Password123!", "mario@example.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmEmail_withValidToken_createsUserAndReturnsUserDto() {
        String token = jwtUtil.generateEmailToken("giovanni", "Password123!", "giovanni@example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/confirmEmail?token={token}",
                HttpMethod.GET,
                null,
                String.class,
                token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"username\":\"giovanni\"");
        assertThat(userRepository.findByUsernameIgnoreCase("giovanni")).isPresent();
    }

    @Test
    void confirmEmail_withAlreadyUsedToken_returnsBadRequest() {
        createUser("giovanni", "giovanni@example.com", "Password123!");
        String token = jwtUtil.generateEmailToken("giovanni", "Password123!", "giovanni@example.com");

        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/confirmEmail?token={token}",
                HttpMethod.GET,
                null,
                String.class,
                token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private User createUser(String username, String email, String rawPassword) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }
}
