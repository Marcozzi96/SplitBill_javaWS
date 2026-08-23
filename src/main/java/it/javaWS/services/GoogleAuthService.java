package it.javaWS.services;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import it.javaWS.models.entities.User;

/**
 * Login/registrazione tramite ID token Google.
 * Se l'email del token è già registrata (anche con password) viene fatto
 * l'account linking: Google garantisce che l'email è verificata.
 * Altrimenti viene creato un utente senza password, che potrà accedere
 * solo via Google (finché non imposta una password dal profilo).
 */
@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    private final UserService userService;
    private final String clientId;

    // Istanziato lazy: la creazione è thread-safe tramite metodo sincronizzato
    private GoogleIdTokenVerifier verifier;

    public GoogleAuthService(UserService userService, @Value("${app.google.client-id:}") String clientId) {
        this.userService = userService;
        this.clientId = clientId;
    }

    @Transactional
    public User loginConGoogle(String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Login con Google non configurato");
        }

        GoogleIdToken.Payload payload = verificaIdToken(idToken);
        String email = payload.getEmail();
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Token Google non valido");
        }

        // Lookup per email canonica: copre sia gli utenti Google sia quelli
        // registrati con password (account linking via email verificata)
        User esistente = userService.getByEmail(email);
        if (esistente != null) {
            return esistente;
        }

        return creaUtente(email);
    }

    // Package-private per essere mockabile nei test (spy): isola la chiamata
    // di rete alla libreria Google dalla logica di business.
    GoogleIdToken.Payload verificaIdToken(String idToken) {
        try {
            GoogleIdToken token = getVerifier().verify(idToken);
            if (token == null) {
                throw new BadCredentialsException("Token Google non valido");
            }
            return token.getPayload();
        } catch (GeneralSecurityException | IOException e) {
            log.warn("Verifica dell'ID token Google fallita: {}", e.getMessage());
            throw new BadCredentialsException("Token Google non valido");
        }
    }

    private synchronized GoogleIdTokenVerifier getVerifier() {
        if (verifier == null) {
            verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(clientId))
                    .build();
        }
        return verifier;
    }

    private User creaUtente(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(generaUsernameDisponibile(email));
        // Password null: l'utente accede solo via Google (vedi AuthController.login)
        user.setPassword(null);
        user.setRegDate(LocalDate.now());

        User creato = userService.createUser(user);
        if (creato == null) {
            // Race condition: un'altra richiesta ha creato l'account nel frattempo
            creato = userService.getByEmail(email);
            if (creato == null) {
                throw new IllegalStateException("Impossibile creare l'utente");
            }
        }
        return creato;
    }

    // Username derivato dal local part dell'email (solo lettere, cifre, punti e
    // underscore); se occupato si aggiunge un suffisso numerico progressivo.
    private String generaUsernameDisponibile(String email) {
        int at = email.lastIndexOf('@');
        String base = (at > 0 ? email.substring(0, at) : email)
                .replaceAll("[^A-Za-z0-9._]", "");
        if (base.isBlank()) {
            base = "utente";
        }

        String candidato = base;
        long suffisso = 1;
        while (userService.getByUsername(candidato) != null) {
            candidato = base + suffisso++;
        }
        return candidato;
    }
}
