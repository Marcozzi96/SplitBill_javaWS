package it.javaWS.controllers;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
import it.javaWS.models.dto.UpdateUserRequest;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.entities.User;
import it.javaWS.services.UserService;
import it.javaWS.utils.JwtUtil;

@RestController
@RequestMapping("/user")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

	private final UserService userService;
	private final JwtUtil jwtUtil;

	public UserController(UserService userService, JwtUtil jwtUtil) {
		this.userService = userService;
		this.jwtUtil = jwtUtil;
	}

	@Operation(summary = "Recupera le informazioni dell'utente autenticato")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Utente trovato con successo"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/me")
	public ResponseEntity<UserDTO> getUser(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(new UserDTO(user));
	}

	@Operation(summary = "Aggiorna le informazioni dell'utente autenticato")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Utente aggiornato con successo"),
		@ApiResponse(responseCode = "400", description = "Dati non validi"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato o password errata"),
		@ApiResponse(responseCode = "409", description = "Username o email già in uso")
	})
	@PutMapping("/update")
	public ResponseEntity<AuthResponse> updateUser(@AuthenticationPrincipal User user, @RequestBody UpdateUserRequest request) {
		User updated = userService.updateUser(user, request);
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
	public ResponseEntity<String> deleteUser(@AuthenticationPrincipal User user) {
		user.setEmail("utente." + user.getId() + LocalDate.now() + "@eliminato");
		user.setUsername("UtenteEliminato" + user.getId() + LocalDate.now());
		user.setPassword("UtenteEliminato" + user.getId() + LocalDate.now());
		user.setDeleted(true);

		userService.updateUser(user);
		return ResponseEntity.ok("Success");
	}

	@Operation(summary = "Invia una richiesta di amicizia a un altro utente")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richiesta inviata con successo"),
		@ApiResponse(responseCode = "208", description = "Richiesta già inviata o esistente"),
		@ApiResponse(responseCode = "400", description = "Errore nella richiesta"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@PostMapping("/sendFriendshipRequest")
	public ResponseEntity<String> sendFriendshipRequest(@AuthenticationPrincipal User user, @RequestParam String name,
			@RequestParam String message) throws Exception {
		Long userId = userService.loadUserByEmailOrUsername(name, name).getId();
		userService.inviaRichiestaAmicizia(user.getId(), userId, message);
		return ResponseEntity.ok("Richiesta inviata");
	}

	@Operation(summary = "Recupera le richieste di amicizia ricevute", description = "Restituisce le richieste ricevute con paginazione")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richieste ricevute recuperate"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/getFriendshipReqReceived")
	public ResponseEntity<Page<FriendshipReqRecDTO>> getFriendshipReqRec(@AuthenticationPrincipal User user,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(userService.getRichiesteAmiciziaRicevuteDto(user.getId(), pageable));
	}

	@Operation(summary = "Recupera le richieste di amicizia inviate", description = "Restituisce le richieste inviate con paginazione")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richieste inviate recuperate"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/getFriendshipReqSent")
	public ResponseEntity<Page<FriendshipReqSenDTO>> getFriendshipReqSen(@AuthenticationPrincipal User user,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(userService.getRichiesteAmiciziaInviateDto(user.getId(), pageable));
	}

	@Operation(summary = "Conta le richieste di amicizia ricevute in attesa")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Conteggio restituito"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/friendshipRequests/count")
	public ResponseEntity<Map<String, Long>> getFriendshipRequestsCount(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(Map.of("count", userService.countRichiesteAmiciziaRicevute(user.getId())));
	}

	@Operation(summary = "Accetta una richiesta di amicizia")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richiesta accettata"),
		@ApiResponse(responseCode = "400", description = "Richiesta non valida"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@PutMapping("/acceptFriendship")
	public ResponseEntity<String> acceptFriendship(@AuthenticationPrincipal User user, @RequestParam Long friendId) {
		userService.accettaRichiestaAmicizia(user.getId(), friendId);
		return ResponseEntity.ok("Richiesta di amicizia accettata");
	}

	@Operation(summary = "Rifiuta una richiesta di amicizia. Può essere usata anche per annullare una richiesta inviata da te")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Richiesta rifiutata"),
		@ApiResponse(responseCode = "400", description = "Errore durante il rifiuto"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@PutMapping("/refuseFriendship")
	public ResponseEntity<String> refuseFriendship(@AuthenticationPrincipal User user, @RequestParam Long friendId) {
		userService.rifiutaRichiestaAmicizia(user.getId(), friendId);
		return ResponseEntity.ok("Richiesta di amicizia rifiutata");
	}

	@Operation(summary = "Annulla un'amicizia esistente")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Amicizia annullata"),
		@ApiResponse(responseCode = "400", description = "Errore durante la rimozione"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@DeleteMapping("/cancelFriendship")
	public ResponseEntity<String> cancelFriendship(@AuthenticationPrincipal User user, @RequestParam Long friendId) {
		userService.rimuoviAmico(user.getId(), friendId);
		return ResponseEntity.ok("Amicizia annullata");
	}

	@Operation(summary = "Recupera la lista degli amici dell'utente", description = "Restituisce gli amici con paginazione")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Lista amici recuperata"),
		@ApiResponse(responseCode = "401", description = "Utente non autenticato")
	})
	@GetMapping("/getFriends")
	public ResponseEntity<Page<UserDTO>> getFriends(@AuthenticationPrincipal User user,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(userService.getAmiciDto(user.getId(), pageable));
	}
}
