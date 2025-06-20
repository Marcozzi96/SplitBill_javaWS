package it.javaWS.controllers;

import it.javaWS.models.dto.GroupDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.services.FriendshipService;
import it.javaWS.services.GroupService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/groups")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

	private final GroupService groupService;
	private final UserGroupRepository userGroupRepository;
	private final FriendshipService friendshipService;

	public GroupController(GroupService groupService, UserGroupRepository userGroupRepository, FriendshipService friendshipService) {
		this.groupService = groupService;
		this.userGroupRepository = userGroupRepository;
		this.friendshipService = friendshipService;
	}

	@Operation(summary = "Crea un nuovo gruppo", description = "Crea un gruppo e aggiunge gli utenti specificati (incluso il creator)")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Gruppo creato con successo"),
			@ApiResponse(responseCode = "400", description = "Errore durante la creazione del gruppo") })
	@PostMapping("/create")
	public ResponseEntity<?> createGroup(@RequestParam String name, @RequestParam String description,
			@RequestBody Set<Long> userIds) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}

		Long userId = userDetails.getId();

		
		if (!friendshipService.areAllFriends(userId, userIds)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Alcuni utenti non sono tuoi amici"));

		}
		userIds.add(userId);
		Group group = groupService.createGroup(name, description, userIds);
		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(groupService.getUsersInGroup(group.getId()));
		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Recupera un gruppo", description = "Restituisce i dettagli del gruppo specificato se l'utente ne fa parte")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Gruppo trovato"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@GetMapping("/{groupId}")
	public ResponseEntity<?> getGroup(@PathVariable Long groupId) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}

		Long userId = userDetails.getId();

		Group group = groupService.getGroup(groupId);
		if (group == null || !userGroupRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}

		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(groupService.getUsersInGroup(groupId));
		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Lista gruppi dell'utente", description = "Restituisce tutti i gruppi a cui l'utente autenticato appartiene")
	@ApiResponse(responseCode = "200", description = "Lista dei gruppi restituita")
	@GetMapping("")
	public ResponseEntity<?> getGroupsByUser() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}

		Long userId = userDetails.getId();

		return ResponseEntity.ok(groupService.getGroupsByUserId(userId).stream().map(GroupDTO::new).toList());
	}

	@Operation(summary = "Aggiungi utenti a un gruppo", description = "Aggiunge una lista di utenti a un gruppo esistente, se l'utente autenticato ne fa parte")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Utenti aggiunti al gruppo"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@PostMapping("/addUsers/{groupId}")
	public ResponseEntity<?> addUsersToGroup(@PathVariable Long groupId, @RequestBody Set<Long> userIds) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}
		Long userId = userDetails.getId();
		if (!friendshipService.areAllFriends(userId, userIds)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Alcuni utenti non sono tuoi amici"));
		}

		userIds.add(userId);

		Group group = groupService.getGroup(groupId);
		if (group == null || !userGroupRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}

		Group updatedGroup = groupService.addUsersToGroup(group, userIds);
		GroupDTO dto = new GroupDTO(updatedGroup);
		dto.setUsers(groupService.getUsersInGroup(groupId));

		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Esci da un gruppo", description = "L'utente autenticato esce dal gruppo specificato")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Utente rimosso dal gruppo"),
			@ApiResponse(responseCode = "400", description = "Gruppo non trovato"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@DeleteMapping("/leave/{groupId}")
	public ResponseEntity<?> leaveTheGroup(@PathVariable Long groupId) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof User userDetails)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Utente non autenticato"));
		}

		Long userId = userDetails.getId();

		Group group = groupService.getGroup(groupId);
		if (group == null || !userGroupRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}

		Set<Long> userIds = new HashSet<>();
		userIds.add(userId);

		Group updatedGroup = groupService.removeUsersFromGroup(groupId, userIds);
		if (updatedGroup == null)
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Gruppo non trovato"));

		GroupDTO dto = new GroupDTO(updatedGroup).setUsers(groupService.getUsersInGroup(groupId));
		return ResponseEntity.ok(dto);
	}
}
