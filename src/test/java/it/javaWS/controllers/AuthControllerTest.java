package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

import it.javaWS.enums.AuthTokenType;
import it.javaWS.models.dto.AuthRequest;
import it.javaWS.models.dto.ForgotPasswordRequest;
import it.javaWS.models.dto.ResetPasswordRequest;
import it.javaWS.models.entities.AuthToken;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.AuthTokenRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.services.AuthTokenService;
import it.javaWS.utils.EmailUtil;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.rate-limit.limit=5")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthTokenRepository authTokenRepository;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    void register_withNewUser_returnsConfirmationMessageAndCreatesOpaqueToken() {
        AuthRequest request = new AuthRequest("luigi", "Password123!", "luigi@example.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Conferma l'email all'indirizzo luigi@example.com");
        assertThat(userRepository.findByUsernameIgnoreCase("luigi")).isEmpty();

        // Il token di conferma è opaco: non è un JWT e non contiene la password
        assertThat(authTokenRepository.findAll()).hasSize(1);
        AuthToken token = authTokenRepository.findAll().get(0);
        assertThat(token.getType()).isEqualTo(AuthTokenType.REGISTRATION);
        assertThat(token.getToken()).doesNotContain(".");
        assertThat(token.getToken()).doesNotContain("Password123!");
        // La password è salvata solo in forma encodata
        assertThat(token.getEncodedPassword()).isNotEqualTo("Password123!");
        assertThat(passwordEncoder.matches("Password123!", token.getEncodedPassword())).isTrue();
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
        authTokenService.createRegistrationToken("giovanni", "giovanni@example.com",
                passwordEncoder.encode("Password123!"));
        String token = authTokenRepository.findAll().get(0).getToken();

        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/confirmEmail?token={token}",
                HttpMethod.GET,
                null,
                String.class,
                token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"username\":\"giovanni\"");
        assertThat(userRepository.findByUsernameIgnoreCase("giovanni")).isPresent();
        // Il token è stato consumato
        assertThat(authTokenRepository.findByToken(token).get().isUsed()).isTrue();
    }

    @Test
    void confirmEmail_withAlreadyUsedToken_returnsBadRequest() {
        authTokenService.createRegistrationToken("giovanni", "giovanni@example.com",
                passwordEncoder.encode("Password123!"));
        String token = authTokenRepository.findAll().get(0).getToken();

        restTemplate.exchange("/auth/confirmEmail?token={token}", HttpMethod.GET, null, String.class, token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/confirmEmail?token={token}",
                HttpMethod.GET,
                null,
                String.class,
                token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirmEmail_withUnknownToken_returnsBadRequest() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/confirmEmail?token={token}",
                HttpMethod.GET,
                null,
                String.class,
                "token-inesistente");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void forgotPassword_withExistingEmail_returnsOkAndCreatesResetToken() {
        User user = createUser("mario", "mario@example.com", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/forgotPassword",
                new ForgotPasswordRequest("mario@example.com"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authTokenRepository.findByUserAndTypeAndUsedFalse(user, AuthTokenType.PASSWORD_RESET)).hasSize(1);
    }

    @Test
    void forgotPassword_withUnknownEmail_returnsOkWithoutToken() {
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/forgotPassword",
                new ForgotPasswordRequest("unknown@example.com"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authTokenRepository.findAll()).isEmpty();
    }

    @Test
    void resetPassword_withValidToken_updatesPasswordAndAllowsLogin() {
        User user = createUser("mario", "mario@example.com", "Password123!");
        AuthToken token = authTokenService.createPasswordResetToken(user);

        ResponseEntity<String> resetResponse = restTemplate.postForEntity("/auth/resetPassword",
                new ResetPasswordRequest(token.getToken(), "NuovaPassword456!"), String.class);

        assertThat(resetResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authTokenRepository.findByToken(token.getToken()).get().isUsed()).isTrue();

        ResponseEntity<String> loginResponse = restTemplate.postForEntity("/auth/login",
                new AuthRequest("mario", "NuovaPassword456!"), String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).contains("\"token\"");
    }

    @Test
    void resetPassword_withAlreadyUsedToken_returnsBadRequest() {
        User user = createUser("mario", "mario@example.com", "Password123!");
        AuthToken token = authTokenService.createPasswordResetToken(user);

        restTemplate.postForEntity("/auth/resetPassword",
                new ResetPasswordRequest(token.getToken(), "NuovaPassword456!"), String.class);
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/resetPassword",
                new ResetPasswordRequest(token.getToken(), "AltraPassword789!"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_withExpiredToken_returnsBadRequest() {
        User user = createUser("mario", "mario@example.com", "Password123!");
        AuthToken token = authTokenService.createPasswordResetToken(user);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        authTokenRepository.save(token);

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/resetPassword",
                new ResetPasswordRequest(token.getToken(), "NuovaPassword456!"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resetPassword_withRegistrationToken_returnsBadRequest() {
        authTokenService.createRegistrationToken("giovanni", "giovanni@example.com",
                passwordEncoder.encode("Password123!"));
        String registrationToken = authTokenRepository.findAll().get(0).getToken();

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/resetPassword",
                new ResetPasswordRequest(registrationToken, "NuovaPassword456!"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_overRateLimit_returnsTooManyRequests() {
        createUser("mario", "mario@example.com", "Password123!");
        AuthRequest request = new AuthRequest("mario", "Password123!");

        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity("/auth/login", request, String.class);
        }
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
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
