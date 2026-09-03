package it.javaWS.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.enums.AuthTokenType;
import it.javaWS.models.entities.AuthToken;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.AuthTokenRepository;
import it.javaWS.utils.InvalidTokenException;

@Service
public class AuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenService.class);

    private static final long REGISTRATION_TOKEN_VALIDITY_HOURS = 24;
    private static final long PASSWORD_RESET_TOKEN_VALIDITY_MINUTES = 15;

    private final AuthTokenRepository authTokenRepository;

    public AuthTokenService(AuthTokenRepository authTokenRepository) {
        this.authTokenRepository = authTokenRepository;
    }

    @Transactional
    public AuthToken createRegistrationToken(String username, String email, String encodedPassword) {
        // Invalida eventuali token di registrazione precedenti per la stessa email non ancora usati
        authTokenRepository.findByEmailAndTypeAndUsedFalse(email, AuthTokenType.REGISTRATION)
                .forEach(t -> {
                    t.setUsed(true);
                    authTokenRepository.save(t);
                });

        AuthToken authToken = new AuthToken();
        authToken.setToken(UUID.randomUUID().toString());
        authToken.setType(AuthTokenType.REGISTRATION);
        authToken.setUsername(username);
        authToken.setEmail(email);
        authToken.setEncodedPassword(encodedPassword);
        authToken.setExpiryDate(LocalDateTime.now().plusHours(REGISTRATION_TOKEN_VALIDITY_HOURS));
        authToken.setUsed(false);
        return authTokenRepository.save(authToken);
    }

    @Transactional
    public AuthToken createPasswordResetToken(User user) {
        // Invalida eventuali token di reset precedenti non ancora usati
        authTokenRepository.findByUserAndTypeAndUsedFalse(user, AuthTokenType.PASSWORD_RESET)
                .forEach(t -> {
                    t.setUsed(true);
                    authTokenRepository.save(t);
                });

        AuthToken authToken = new AuthToken();
        authToken.setToken(UUID.randomUUID().toString());
        authToken.setType(AuthTokenType.PASSWORD_RESET);
        authToken.setUser(user);
        authToken.setExpiryDate(LocalDateTime.now().plusMinutes(PASSWORD_RESET_TOKEN_VALIDITY_MINUTES));
        authToken.setUsed(false);
        return authTokenRepository.save(authToken);
    }

    @Transactional
    public AuthToken validateAndConsume(String token, AuthTokenType expectedType) {
        AuthToken authToken = authTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Token non valido"));

        if (authToken.getType() != expectedType) {
            throw new InvalidTokenException("Token non valido");
        }
        if (authToken.isUsed()) {
            throw new InvalidTokenException("Token già utilizzato");
        }
        if (authToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new InvalidTokenException("Token scaduto");
        }

        authToken.setUsed(true);
        return authTokenRepository.save(authToken);
    }

    // Pulizia giornaliera dei token scaduti (registrazione e reset password)
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteExpiredTokens() {
        long deleted = authTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Eliminati {} token auth scaduti", deleted);
        }
    }
}
