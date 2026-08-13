package it.javaWS.controllers;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import it.javaWS.enums.AuthTokenType;
import it.javaWS.models.dto.AuthRequest;
import it.javaWS.models.dto.AuthResponse;
import it.javaWS.models.dto.ForgotPasswordRequest;
import it.javaWS.models.dto.ResetPasswordRequest;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.entities.AuthToken;
import it.javaWS.models.entities.User;
import it.javaWS.services.AuthTokenService;
import it.javaWS.services.UserService;
import it.javaWS.utils.EmailUtil;
import it.javaWS.utils.JwtUtil;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final EmailUtil emailUtil;
    private final AuthTokenService authTokenService;

    public AuthController(AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder,
            UserService userService, JwtUtil jwtUtil, EmailUtil emailUtil, AuthTokenService authTokenService) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.emailUtil = emailUtil;
        this.authTokenService = authTokenService;
    }

    @Operation(
        summary = "Login utente",
        description = "Effettua l'autenticazione e restituisce un JWT token se le credenziali sono corrette"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Autenticazione avvenuta con successo"),
        @ApiResponse(responseCode = "401", description = "Credenziali non valide"),
        @ApiResponse(responseCode = "429", description = "Troppe richieste, riprovare più tardi")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request) {
        try {
            User user = userService.loadUserByEmailOrUsername(request.getEmail(), request.getUsername());
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
            String token = jwtUtil.generateToken(user);
            return ResponseEntity.ok(new AuthResponse(token, new UserDTO(user)));
        } catch (AuthenticationException e) {
            throw new BadCredentialsException("Credenziali non valide");
        }
    }

    @Operation(
        summary = "Registrazione utente",
        description = "Registra un nuovo utente e invia una mail di conferma con un token opaco a scadenza"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Registrazione avvenuta con successo"),
        @ApiResponse(responseCode = "400", description = "Username o email già utilizzati"),
        @ApiResponse(responseCode = "429", description = "Troppe richieste, riprovare più tardi")
    })
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody AuthRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());

        if (userService.existsByUsernameOrEmail(user)) {
            throw new IllegalStateException("Username o Email già utilizzati");
        }

        AuthToken authToken = authTokenService.createRegistrationToken(
                user.getUsername(), user.getEmail(), passwordEncoder.encode(request.getPassword()));
        emailUtil.sendEmail(user.getEmail(), "SplitBill registration",
                emailUtil.creaCorpoEmailConferma(user.getUsername(), authToken.getToken()));

        return ResponseEntity.ok("Conferma l'email all'indirizzo " + user.getEmail());
    }

    @Operation(
        summary = "Conferma registrazione via email",
        description = "Conferma la registrazione di un utente tramite il token opaco inviato via email"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email confermata e utente creato"),
        @ApiResponse(responseCode = "400", description = "Token scaduto, già utilizzato o non valido")
    })
    @GetMapping("/confirmEmail")
    public ResponseEntity<UserDTO> confirmRegistration(@RequestParam String token) {
        AuthToken authToken = authTokenService.validateAndConsume(token, AuthTokenType.REGISTRATION);

        User user = new User();
        user.setUsername(authToken.getUsername());
        user.setEmail(authToken.getEmail());

        if (userService.existsByUsernameOrEmail(user)) {
            throw new IllegalStateException("Token già utilizzato");
        }

        user.setRegDate(LocalDate.now());
        user.setPassword(authToken.getEncodedPassword());

        User newUser = userService.createUser(user);
        if (newUser == null) {
            throw new IllegalStateException("Token non valido");
        }

        emailUtil.sendEmail(newUser.getEmail(), "SplitBill registration",
                emailUtil.creaCorpoEmailBenvenuto(newUser.getUsername()));
        return ResponseEntity.ok(new UserDTO(newUser));
    }

    @Operation(
        summary = "Richiesta recupero password",
        description = "Invia una mail con un token opaco a breve scadenza per reimpostare la password. " +
                "La risposta è sempre positiva per non rivelare l'esistenza dell'indirizzo email."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Richiesta elaborata (la mail viene inviata solo se l'indirizzo è registrato)"),
        @ApiResponse(responseCode = "429", description = "Troppe richieste, riprovare più tardi")
    })
    @PostMapping("/forgotPassword")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        User user = userService.getByEmail(request.getEmail());
        if (user != null) {
            AuthToken authToken = authTokenService.createPasswordResetToken(user);
            emailUtil.sendEmail(user.getEmail(), "SplitBill reset password",
                    emailUtil.creaCorpoEmailResetPassword(user.getUsername(), authToken.getToken()));
        }
        return ResponseEntity.ok("Se l'indirizzo email è registrato, riceverai una mail con le istruzioni per il reset");
    }

    @Operation(
        summary = "Reset password",
        description = "Reimposta la password dell'utente tramite il token opaco ricevuto via email"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Password aggiornata con successo"),
        @ApiResponse(responseCode = "400", description = "Token scaduto, già utilizzato o non valido"),
        @ApiResponse(responseCode = "429", description = "Troppe richieste, riprovare più tardi")
    })
    @PostMapping("/resetPassword")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest request) {
        AuthToken authToken = authTokenService.validateAndConsume(request.getToken(), AuthTokenType.PASSWORD_RESET);

        User user = authToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userService.updateUser(user);

        return ResponseEntity.ok("Password aggiornata con successo");
    }
}
