package it.javaWS.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.AuthResponse;
import it.javaWS.models.dto.FriendshipReqRecDTO;
import it.javaWS.models.dto.FriendshipReqSenDTO;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.entities.User;
import it.javaWS.services.UserService;
import it.javaWS.utils.JwtUtil;
import jakarta.persistence.EntityNotFoundException;

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

	@Operation(summary = "Recupera le informazioni dell'utente autenticato")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Utente trovato con successo"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/me")
	public ResponseEntity<?> getUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		return ResponseEntity.ok(new UserDTO(userDetails));
	}

	@Operation(summary = "Aggiorna le informazioni dell'utente autenticato")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Utente aggiornato con successo"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@PutMapping("/update")
	public ResponseEntity<?> updateUser(@RequestBody User updatedUser) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		User userFromDB = userDetails;
		if (updatedUser.getEmail() != null)
			userFromDB.setEmail(updatedUser.getEmail().toLowerCase());
		if (updatedUser.getUsername() != null)
			userFromDB.setUsername(updatedUser.getUsername());
		if (updatedUser.getPassword() != null) {
			userFromDB.setPassword(passwordEncoder.encode(updatedUser.getPassword()));
		}
		User updated = userService.updateUser(userFromDB);
		String newToken = jwtUtil.generateToken(updated);
		return ResponseEntity.ok(new AuthResponse(newToken, new UserDTO(updated)));
	}

	@Operation(summary = "Elimina (soft delete) l'account dell'utente autenticato")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Utente eliminato con successo"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato"),
		@ApiResponse(responseCode = "500", description = "Errore durante l'eliminazione dell'utente")
	})
	@DeleteMapping("/delete")
	public ResponseEntity<?> deleteUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		userDetails.setEmail("utente." + userDetails.getId() + LocalDate.now() + "@eliminato");
		userDetails.setUsername("UtenteEliminato" + userDetails.getId() + LocalDate.now());
		userDetails.setPassword("UtenteEliminato" + userDetails.getId() + LocalDate.now());

		User updated = userService.updateUser(userDetails);
		if (updated == null) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Errore in fase di updateUser"));
		}
		return ResponseEntity.ok("Success");
	}

	@Operation(summary = "Invia una richiesta di amicizia a un altro utente")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richiesta inviata con successo"),
		@ApiResponse(responseCode = "208", description = "Richiesta già inviata o esistente"),
		@ApiResponse(responseCode = "400", description = "Errore nella richiesta"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/sendFriendshipRequest")
	public ResponseEntity<?> sendFriendshipRequest(@RequestParam String name, @RequestBody String message) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		try {
			Long userId = userService.loadUserByEmailOrUsername(name, name).getId();
			userService.inviaRichiestaAmicizia(userDetails.getId(), userId, message);
			return ResponseEntity.ok("Richiesta inviata");
		} catch (IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.ALREADY_REPORTED).body(Map.of("error", e.getMessage()));
		} catch (EntityNotFoundException | IllegalArgumentException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Errore generico."));
		}
	}

	@Operation(summary = "Recupera le richieste di amicizia ricevute")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richieste ricevute recuperate"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/getFriendshipReqReceived")
	public ResponseEntity<?> getFriendshipReqRec() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		return ResponseEntity.ok(userService.getRichiesteAmiciziaRicevute(userDetails.getId()).stream()
				.map(FriendshipReqRecDTO::new).toList());
	}

	@Operation(summary = "Recupera le richieste di amicizia inviate")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richieste inviate recuperate"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/getFriendshipReqSent")
	public ResponseEntity<?> getFriendshipReqSen() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		return ResponseEntity.ok(userService.getRichiesteAmiciziaInviate(userDetails.getId()).stream()
				.map(FriendshipReqSenDTO::new).toList());
	}

	@Operation(summary = "Accetta una richiesta di amicizia")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richiesta accettata"),
		@ApiResponse(responseCode = "400", description = "Richiesta non valida"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/acceptFriendship")
	public ResponseEntity<?> acceptFriendship(@RequestParam Long friendId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		try {
			userService.accettaRichiestaAmicizia(userDetails.getId(), friendId);
			return ResponseEntity.ok("Richiesta di amicizia accettata");
		} catch (EntityNotFoundException | IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Errore generico."));
		}
	}

	@Operation(summary = "Rifiuta una richiesta di amicizia. Può essere usata anche per annullare una richiesta inviata da te")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richiesta rifiutata"),
		@ApiResponse(responseCode = "400", description = "Errore durante il rifiuto"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/refuseFriendship")
	public ResponseEntity<?> refuseFriendship(@RequestParam Long friendId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		try {
			userService.rifiutaRichiestaAmicizia(userDetails.getId(), friendId);
			return ResponseEntity.ok("Richiesta di amicizia rifiutata");
		} catch (EntityNotFoundException | IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Errore generico."));
		}
	}

	@Operation(summary = "Annulla un'amicizia esistente")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Amicizia annullata"),
		@ApiResponse(responseCode = "400", description = "Errore durante la rimozione"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@DeleteMapping("/cancelFriendship")
	public ResponseEntity<?> cancelFriendship(@RequestParam Long friendId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		try {
			userService.rimuoviAmico(userDetails.getId(), friendId);
			return ResponseEntity.ok("Amicizia annullata");
		} catch (EntityNotFoundException | IllegalStateException e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Errore generico."));
		}
	}

	@Operation(summary = "Recupera la lista degli amici dell'utente")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Lista amici recuperata"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/getFriends")
	public ResponseEntity<?> getFriends() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		return ResponseEntity.ok(userService.getAmici(userDetails.getId()).stream().map(UserDTO::new).toList());
	}
}
