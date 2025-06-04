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

import it.javaWS.javaws.dto.AuthRequest;
import it.javaWS.javaws.dto.AuthResponse;
import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.User;
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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
        	
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
			User user = userService.loadUserByUsername(request.getUsername());
//            UserDetails user = userService.loadUserByUsername(request.getUsername());
            String token = jwtUtil.generateToken(user);
            return ResponseEntity.ok(new AuthResponse(token, new UserDTO(user)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
    	
    	if(userService.existsByUsernameOrEmail(user)) {
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Username o Email già utilizzati"));
    	}
//        user.setRegDate(LocalDate.now());
        //user.setPassword(passwordEncoder.encode(user.getPassword()));
        String token = jwtUtil.generateEmailToken(user.getUsername(),user.getPassword(), user.getEmail());
//        User newUser = userService.createUser(user);
//        if (newUser == null)
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Username o Email già utilizzati"));

        emailUtil.sendEmail(user.getEmail(), "SplitBill registration", emailUtil.creaCorpoEmailConferma(user.getUsername(),token));
        
        return ResponseEntity.ok("Conferma l'email all'indirizzo " + user.getEmail());
    }
    
    @GetMapping("/confirmEmail")
    public ResponseEntity<?> confirmRegistration(@RequestParam String token) {
    	String username = jwtUtil.extractUsername(token);
    	String password = jwtUtil.extractPassword(token);
    	String email = jwtUtil.extractEmail(token);
    	
    	User user = new User();
    	user.setUsername(username);
    	user.setEmail(email);
    	
    	if(jwtUtil.isTokenExpired(token)) {
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Token scaduto"));
    	}
    	
    	if(userService.existsByUsernameOrEmail(user)) {
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Token già utilizzato"));
    	}
    	
    	user.setRegDate(LocalDate.now());
    	user.setPassword(passwordEncoder.encode(password));
    	
    	User newUser = userService.createUser(user);
      if (newUser == null)
          return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Token non valido"));

    	
    	emailUtil.sendEmail(newUser.getEmail(), "SplitBill registration", emailUtil.creaCorpoEmailBenvenuto(newUser.getUsername()));
    	
    	return ResponseEntity.ok(new UserDTO(newUser));
    }
}
