package it.javaWS.javaws.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import it.javaWS.javaws.models.dto.AuthRequest;
import it.javaWS.javaws.models.dto.AuthResponse;
import it.javaWS.javaws.models.dto.UserDTO;
import it.javaWS.javaws.models.entities.User;
import it.javaWS.javaws.services.UserService;
import it.javaWS.javaws.utils.EmailUtil;
import it.javaWS.javaws.utils.JwtUtil;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final EmailUtil emailUtil;

    public AuthController(AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder, UserService userService, JwtUtil jwtUtil, EmailUtil emailUtil) {
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.emailUtil = emailUtil;
    }

    @Operation(
        summary = "Login utente",
        description = "Effettua l'autenticazione e restituisce un JWT token se le credenziali sono corrette"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Autenticazione avvenuta con successo"),
        @ApiResponse(responseCode = "401", description = "Credenziali non valide")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
        	User user = userService.loadUserByEmailOrUsername(request.getEmail(), request.getUsername());
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
            String token = jwtUtil.generateToken(user);
            return ResponseEntity.ok(new AuthResponse(token, new UserDTO(user)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
        summary = "Registrazione utente",
        description = "Registra un nuovo utente e invia una mail di conferma"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Registrazione avvenuta con successo"),
        @ApiResponse(responseCode = "400", description = "Username o email già utilizzati")
    })
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
    	User user = new User();
    	user.setUsername(request.getUsername());
    	user.setEmail(request.getEmail());
    	user.setPassword(request.getPassword());
        if (userService.existsByUsernameOrEmail(user)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Username o Email già utilizzati"));
        }

        String token = jwtUtil.generateEmailToken(user.getUsername(), user.getPassword(), user.getEmail());
        emailUtil.sendEmail(user.getEmail(), "SplitBill registration", emailUtil.creaCorpoEmailConferma(user.getUsername(), token));

        return ResponseEntity.ok("Conferma l'email all'indirizzo " + user.getEmail());
    }

    @Operation(
        summary = "Conferma registrazione via email",
        description = "Conferma la registrazione di un utente tramite il token inviato via email"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Email confermata e utente creato"),
        @ApiResponse(responseCode = "400", description = "Token scaduto o non valido")
    })
    @GetMapping("/confirmEmail")
    public ResponseEntity<?> confirmRegistration(@RequestParam String token) {
        String username = jwtUtil.extractUsername(token);
        String password = jwtUtil.extractPassword(token);
        String email = jwtUtil.extractEmail(token);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);

        if (jwtUtil.isTokenExpired(token)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Token scaduto"));
        }

        if (userService.existsByUsernameOrEmail(user)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Token già utilizzato"));
        }

        user.setRegDate(LocalDate.now());
        user.setPassword(passwordEncoder.encode(password));

        User newUser = userService.createUser(user);
        if (newUser == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Token non valido"));
        }

        emailUtil.sendEmail(newUser.getEmail(), "SplitBill registration", emailUtil.creaCorpoEmailBenvenuto(newUser.getUsername()));
        return ResponseEntity.ok(new UserDTO(newUser));
    }
}
