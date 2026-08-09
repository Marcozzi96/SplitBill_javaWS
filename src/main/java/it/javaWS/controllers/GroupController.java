package it.javaWS.controllers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.GroupDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.services.FriendshipService;
import it.javaWS.services.GroupService;
import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/groups")
@SecurityRequirement(name = "bearerAuth")
public class GroupController {

	private final GroupService groupService;
	private final FriendshipService friendshipService;

	public GroupController(GroupService groupService, FriendshipService friendshipService) {
		this.groupService = groupService;
		this.friendshipService = friendshipService;
	}

	@Operation(summary = "Crea un nuovo gruppo", description = "Crea un gruppo e aggiunge gli utenti specificati (incluso il creator)")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Gruppo creato con successo"),
			@ApiResponse(responseCode = "400", description = "Errore durante la creazione del gruppo") })
	@PostMapping("/create")
	public ResponseEntity<GroupDTO> createGroup(@AuthenticationPrincipal User user, @RequestParam String name,
			@RequestParam String description, @RequestBody Set<Long> userIds) {
		Long userId = user.getId();

		if (!friendshipService.areAllFriends(userId, userIds)) {
			throw new IllegalArgumentException("Alcuni utenti non sono tuoi amici");
		}
		userIds.add(userId);
		Group group = groupService.createGroup(name, description, userIds, userId);
		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(groupService.getUsersInGroup(group.getId()));
		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Recupera un gruppo", description = "Restituisce i dettagli del gruppo specificato se l'utente ne fa parte")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Gruppo trovato"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@GetMapping("/{groupId}")
	public ResponseEntity<GroupDTO> getGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId) {
		Group group = groupService.getGroup(groupId);
		checkMembership(group, user.getId());

		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(groupService.getUsersInGroup(groupId));
		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Lista gruppi dell'utente", description = "Restituisce tutti i gruppi a cui l'utente autenticato appartiene")
	@ApiResponse(responseCode = "200", description = "Lista dei gruppi restituita")
	@GetMapping("")
	public ResponseEntity<List<GroupDTO>> getGroupsByUser(@AuthenticationPrincipal User user) {
		return ResponseEntity.ok(groupService.getGroupsByUserId(user.getId()).stream().map(GroupDTO::new).toList());
	}

	@Operation(summary = "Aggiungi utenti a un gruppo", description = "Aggiunge una lista di utenti a un gruppo esistente, se l'utente autenticato ne fa parte")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Utenti aggiunti al gruppo"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@PostMapping("/addUsers/{groupId}")
	public ResponseEntity<GroupDTO> addUsersToGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId,
			@RequestBody Set<Long> userIds) {
		Long userId = user.getId();
		if (!friendshipService.areAllFriends(userId, userIds)) {
			throw new IllegalArgumentException("Alcuni utenti non sono tuoi amici");
		}

		userIds.add(userId);

		Group group = groupService.getGroup(groupId);
		checkMembership(group, userId);

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
	public ResponseEntity<GroupDTO> leaveTheGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId) {
		Long userId = user.getId();

		Group group = groupService.getGroup(groupId);
		checkMembership(group, userId);

		Set<Long> userIds = new HashSet<>();
		userIds.add(userId);

		Group updatedGroup = groupService.removeUsersFromGroup(groupId, userIds);
		if (updatedGroup == null)
			throw new EntityNotFoundException("Gruppo non trovato");

		GroupDTO dto = new GroupDTO(updatedGroup).setUsers(groupService.getUsersInGroup(groupId));
		return ResponseEntity.ok(dto);
	}

	private void checkMembership(Group group, Long userId) {
		if (group == null || !groupService.existsByGroupIdAndUserId(group.getId(), userId)) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
	}
}
