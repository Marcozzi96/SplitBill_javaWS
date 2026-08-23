package it.javaWS.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import it.javaWS.models.dto.GoogleLoginRequest;
import it.javaWS.models.dto.ResetPasswordRequest;
import it.javaWS.models.entities.AuthToken;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.AuthTokenRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.services.AuthTokenService;
import it.javaWS.services.UserService;
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
    void register_withDuplicateEmailDifferentCase_returnsBadRequest() {
        createUser("mario", "mario@example.com", "Password123!");
        AuthRequest request = new AuthRequest("other", "Password123!", "MARIO@example.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_withGmailDotVariantOfExistingEmail_returnsBadRequest() {
        createUser("jorge", "giorgioarmignacco97@gmail.com", "Password123!");
        // Per Gmail i punti nel local part non contano: è la stessa casella
        AuthRequest request = new AuthRequest("giorgio", "Password123!", "giorgio.armignacco97@gmail.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_withGmailDotsAndUpperCase_keepsTypedEmailAndCanonicalizesForLookup() {
        AuthRequest request = new AuthRequest("luigi", "Password123!", "Luigi.Verdi@gmail.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // L'email nel token (e poi nel profilo) resta come l'ha scritta l'utente
        AuthToken token = authTokenRepository.findAll().get(0);
        assertThat(token.getEmail()).isEqualTo("Luigi.Verdi@gmail.com");

        // Dopo la conferma: email salvata com'è, canonical normalizzata
        restTemplate.exchange("/auth/confirmEmail?token={token}", HttpMethod.GET, null, String.class, token.getToken());
        User saved = userRepository.findByUsernameIgnoreCase("luigi").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("Luigi.Verdi@gmail.com");
        assertThat(saved.getEmailCanonical()).isEqualTo("luigiverdi@gmail.com");

        // Il login funziona anche con la forma senza punti e minuscola
        ResponseEntity<String> login = restTemplate.postForEntity("/auth/login",
                new AuthRequest(null, "Password123!", "luigiverdi@gmail.com"), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("\"token\"");
    }

    @Test
    void repository_withDuplicateEmail_violatesUniqueConstraint() {
        createUser("mario", "mario@example.com", "Password123!");
        User duplicate = new User();
        duplicate.setUsername("other");
        duplicate.setEmail("mario@example.com");
        duplicate.setEmailCanonical(UserService.normalizeEmail("mario@example.com"));
        duplicate.setPassword(passwordEncoder.encode("Password123!"));
        duplicate.setRegDate(LocalDate.now());

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
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
    void login_googleAccountWithoutPassword_returnsUnauthorizedWithGoogleMessage() {
        createGoogleUser("mario", "mario@gmail.com");
        AuthRequest request = new AuthRequest("mario", "Password123!");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("Questo account usa l'accesso con Google");
    }

    @Test
    void googleLogin_whenNotConfigured_returnsBadRequest() {
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/google",
                new GoogleLoginRequest("token-finto"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Login con Google non configurato");
    }

    @Test
    void googleLogin_idTokenMancante_returnsBadRequest() {
        ResponseEntity<String> response = restTemplate.postForEntity("/auth/google",
                new GoogleLoginRequest(null), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("ID token mancante");
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
        user.setEmailCanonical(UserService.normalizeEmail(email));
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }

    // Utente registrato via Google: nessuna password
    private User createGoogleUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setEmailCanonical(UserService.normalizeEmail(email));
        user.setPassword(null);
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }
}
