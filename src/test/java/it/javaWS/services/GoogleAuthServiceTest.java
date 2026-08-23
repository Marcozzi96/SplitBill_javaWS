package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

import it.javaWS.models.entities.User;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UserService userService;

    // Spy per isolare la verifica del token (chiamata di rete alla libreria Google)
    private GoogleAuthService newService(String clientId) {
        return spy(new GoogleAuthService(userService, clientId));
    }

    private GoogleIdToken.Payload payload(String email, boolean emailVerified) {
        return new GoogleIdToken.Payload().setEmail(email).setEmailVerified(emailVerified);
    }

    private User buildUser(Long id, String username, String email, String password) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        return user;
    }

    @Test
    void loginConGoogle_emailNuova_creaUtenteSenzaPassword() {
        GoogleAuthService service = newService("client-id");
        doReturn(payload("mario.rossi@gmail.com", true)).when(service).verificaIdToken("token-valido");
        when(userService.getByEmail("mario.rossi@gmail.com")).thenReturn(null);
        when(userService.getByUsername("mario.rossi")).thenReturn(null);
        when(userService.createUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.loginConGoogle("token-valido");

        assertThat(result.getEmail()).isEqualTo("mario.rossi@gmail.com");
        assertThat(result.getUsername()).isEqualTo("mario.rossi");
        assertThat(result.getPassword()).isNull();
        assertThat(result.getRegDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void loginConGoogle_usernameOccupato_aggiungeSuffissoNumerico() {
        GoogleAuthService service = newService("client-id");
        doReturn(payload("mario@example.com", true)).when(service).verificaIdToken("token-valido");
        when(userService.getByEmail("mario@example.com")).thenReturn(null);
        when(userService.getByUsername("mario")).thenReturn(buildUser(9L, "mario", "altro@example.com", "pwd"));
        when(userService.getByUsername("mario1")).thenReturn(null);
        when(userService.createUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.loginConGoogle("token-valido");

        assertThat(result.getUsername()).isEqualTo("mario1");
    }

    @Test
    void loginConGoogle_localPartConCaratteriNonValidi_sanitizzaUsername() {
        GoogleAuthService service = newService("client-id");
        doReturn(payload("mario+test@example.com", true)).when(service).verificaIdToken("token-valido");
        when(userService.getByEmail("mario+test@example.com")).thenReturn(null);
        when(userService.getByUsername("mariotest")).thenReturn(null);
        when(userService.createUser(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = service.loginConGoogle("token-valido");

        assertThat(result.getUsername()).isEqualTo("mariotest");
    }

    @Test
    void loginConGoogle_emailEsistente_accountLinkingSenzaCreazione() {
        GoogleAuthService service = newService("client-id");
        User esistente = buildUser(1L, "mario", "mario@gmail.com", "encodedPwd");
        doReturn(payload("mario@gmail.com", true)).when(service).verificaIdToken("token-valido");
        when(userService.getByEmail("mario@gmail.com")).thenReturn(esistente);

        User result = service.loginConGoogle("token-valido");

        assertThat(result).isEqualTo(esistente);
        verify(userService, never()).createUser(any());
    }

    @Test
    void loginConGoogle_tokenNonValido_throwsBadCredentials() {
        GoogleAuthService service = newService("client-id");
        doThrow(new BadCredentialsException("Token Google non valido"))
                .when(service).verificaIdToken(anyString());

        assertThatThrownBy(() -> service.loginConGoogle("token-fasullo"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Token Google non valido");
        verify(userService, never()).getByEmail(anyString());
    }

    @Test
    void loginConGoogle_emailNonVerificata_throwsBadCredentials() {
        GoogleAuthService service = newService("client-id");
        doReturn(payload("mario@gmail.com", false)).when(service).verificaIdToken("token-valido");

        assertThatThrownBy(() -> service.loginConGoogle("token-valido"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Token Google non valido");
        verify(userService, never()).getByEmail(anyString());
    }

    @Test
    void loginConGoogle_clientIdNonConfigurato_throwsIllegalState() {
        GoogleAuthService service = newService("");

        assertThatThrownBy(() -> service.loginConGoogle("token-valido"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Login con Google non configurato");
    }
}
