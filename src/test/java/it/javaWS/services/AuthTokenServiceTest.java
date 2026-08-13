package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.javaWS.enums.AuthTokenType;
import it.javaWS.models.entities.AuthToken;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.AuthTokenRepository;
import it.javaWS.utils.InvalidTokenException;

@ExtendWith(MockitoExtension.class)
class AuthTokenServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @InjectMocks
    private AuthTokenService authTokenService;

    @Test
    void createRegistrationToken_savesTokenWithRegistrationData() {
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthToken token = authTokenService.createRegistrationToken("mario", "mario@example.com", "encoded");

        assertThat(token.getToken()).isNotBlank();
        assertThat(token.getType()).isEqualTo(AuthTokenType.REGISTRATION);
        assertThat(token.getUsername()).isEqualTo("mario");
        assertThat(token.getEmail()).isEqualTo("mario@example.com");
        assertThat(token.getEncodedPassword()).isEqualTo("encoded");
        assertThat(token.getExpiryDate()).isAfter(LocalDateTime.now().plusHours(23));
        assertThat(token.isUsed()).isFalse();
        verify(authTokenRepository).save(token);
    }

    @Test
    void createPasswordResetToken_invalidatesPreviousTokens() {
        User user = new User();
        AuthToken previous = new AuthToken();
        previous.setUsed(false);
        when(authTokenRepository.findByUserAndTypeAndUsedFalse(user, AuthTokenType.PASSWORD_RESET))
                .thenReturn(List.of(previous));
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthToken token = authTokenService.createPasswordResetToken(user);

        assertThat(previous.isUsed()).isTrue();
        assertThat(token.getType()).isEqualTo(AuthTokenType.PASSWORD_RESET);
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getExpiryDate()).isBefore(LocalDateTime.now().plusMinutes(16));
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void validateAndConsume_withValidToken_marksUsedAndReturnsIt() {
        AuthToken token = validToken(AuthTokenType.REGISTRATION);
        when(authTokenRepository.findByToken("abc")).thenReturn(Optional.of(token));
        when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(i -> i.getArgument(0));

        AuthToken result = authTokenService.validateAndConsume("abc", AuthTokenType.REGISTRATION);

        assertThat(result.isUsed()).isTrue();
        verify(authTokenRepository).save(token);
    }

    @Test
    void validateAndConsume_withUnknownToken_throwsInvalidTokenException() {
        when(authTokenRepository.findByToken("abc")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authTokenService.validateAndConsume("abc", AuthTokenType.REGISTRATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateAndConsume_withWrongType_throwsInvalidTokenException() {
        when(authTokenRepository.findByToken("abc"))
                .thenReturn(Optional.of(validToken(AuthTokenType.PASSWORD_RESET)));

        assertThatThrownBy(() -> authTokenService.validateAndConsume("abc", AuthTokenType.REGISTRATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateAndConsume_withUsedToken_throwsInvalidTokenException() {
        AuthToken token = validToken(AuthTokenType.REGISTRATION);
        token.setUsed(true);
        when(authTokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authTokenService.validateAndConsume("abc", AuthTokenType.REGISTRATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void validateAndConsume_withExpiredToken_throwsInvalidTokenException() {
        AuthToken token = validToken(AuthTokenType.REGISTRATION);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));
        when(authTokenRepository.findByToken("abc")).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authTokenService.validateAndConsume("abc", AuthTokenType.REGISTRATION))
                .isInstanceOf(InvalidTokenException.class);
    }

    private AuthToken validToken(AuthTokenType type) {
        AuthToken token = new AuthToken();
        token.setToken("abc");
        token.setType(type);
        token.setExpiryDate(LocalDateTime.now().plusHours(1));
        token.setUsed(false);
        return token;
    }
}
