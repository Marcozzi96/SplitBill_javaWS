package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.AuthResponse;
import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.services.UserService;
import it.javaWS.javaws.utils.JwtUtil;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/user")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

	private final UserService userService;
	private final JwtUtil jwtUtil;
	private final PasswordEncoder passwordEncoder;

	public UserController(UserService userService, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
		this.userService = userService;
		this.jwtUtil = jwtUtil;
		this.passwordEncoder = passwordEncoder;
	}

	@GetMapping("/me")
	public ResponseEntity<?> getUser() {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		return ResponseEntity.ok(new UserDTO(userDetails));

	}

	@PutMapping("/update")
	public ResponseEntity<?> updateUser(@RequestBody User updatedUser) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		User userFromDB = userDetails;
		if (updatedUser.getEmail() != null)
			userFromDB.setEmail(updatedUser.getEmail());
		if (updatedUser.getUsername() != null)
			userFromDB.setUsername(updatedUser.getUsername());
		if (updatedUser.getPassword() != null) {
			userFromDB.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
		}
		User updated = userService.updateUser(userFromDB);

		// CREA UN NUOVO TOKEN
		String newToken = jwtUtil.generateToken(updated);

		return ResponseEntity.ok(new AuthResponse(newToken, new UserDTO(updated)));

	}

	// Non elimino veramente l'account, ma modifico tutti i dati sensibili.
	// La vera eliminazione dell'utente creerebbe problemi nelle relazioni per la
	// gestione dei conti di gruppo.
	@DeleteMapping("/delete")
	public ResponseEntity<?> deleteUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		User userFromDB = userDetails;

		userFromDB.setEmail("utente." + userFromDB.getId() + LocalDate.now() + "@eliminato");
		userFromDB.setUsername("UtenteEliminato" + userFromDB.getId() + LocalDate.now());
		userFromDB.setPassword("UtenteEliminato" + userFromDB.getId() + LocalDate.now());

		User updated = userService.updateUser(userFromDB);

		if (updated == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("error", "Errore in fase di updateUser"));
		}
		return ResponseEntity.ok("Success");

	}

}
