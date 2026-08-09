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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.GroupDTO;
import it.javaWS.models.dto.GroupMemberDTO;
import it.javaWS.models.dto.SettlementDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.services.FriendshipService;
import it.javaWS.services.GroupService;

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

	@Operation(summary = "Esci da un gruppo", description = "L'utente autenticato esce dal gruppo specificato. Se è l'ultimo membro, il gruppo viene eliminato.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Utente rimosso dal gruppo o gruppo eliminato"),
			@ApiResponse(responseCode = "400", description = "Gruppo non trovato"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@DeleteMapping("/leave/{groupId}")
	public ResponseEntity<?> leaveTheGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId) {
		Long userId = user.getId();

		Group group = groupService.getGroup(groupId);
		checkMembership(group, userId);

		Set<Long> userIds = new HashSet<>();
		userIds.add(userId);

		Group updatedGroup = groupService.removeUsersFromGroup(groupId, userIds);
		if (updatedGroup == null) {
			return ResponseEntity.ok("Gruppo eliminato");
		}

		GroupDTO dto = new GroupDTO(updatedGroup).setUsers(groupService.getUsersInGroup(groupId));
		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Lista membri attivi di un gruppo", description = "Restituisce i membri attivi del gruppo specificato")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Lista membri restituita"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "404", description = "Gruppo non trovato") })
	@GetMapping("/{groupId}/members")
	public ResponseEntity<List<GroupMemberDTO>> getGroupMembers(@AuthenticationPrincipal User user,
			@PathVariable Long groupId) {
		Group group = groupService.getGroup(groupId);
		checkMembership(group, user.getId());
		return ResponseEntity.ok(groupService.getActiveMembers(groupId));
	}

	@Operation(summary = "Modifica un gruppo", description = "Modifica nome e descrizione di un gruppo. Solo l'admin può eseguire questa operazione.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Gruppo modificato con successo"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "403", description = "L'utente non è admin del gruppo"),
			@ApiResponse(responseCode = "404", description = "Gruppo non trovato") })
	@PutMapping("/{groupId}")
	public ResponseEntity<GroupDTO> updateGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId,
			@RequestParam String name, @RequestParam(required = false) String description) {
		Group updatedGroup = groupService.updateGroup(groupId, name, description, user.getId());
		GroupDTO dto = new GroupDTO(updatedGroup);
		dto.setUsers(groupService.getUsersInGroup(groupId));
		return ResponseEntity.ok(dto);
	}

	@Operation(summary = "Stato dei debiti/crediti pendenti", description = "Restituisce l'elenco dei debiti/crediti pendenti tra i membri attivi del gruppo")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Stato restituito"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "404", description = "Gruppo non trovato") })
	@GetMapping("/{groupId}/settlement-status")
	public ResponseEntity<List<SettlementDTO>> getSettlementStatus(@AuthenticationPrincipal User user,
			@PathVariable Long groupId) {
		Group group = groupService.getGroup(groupId);
		checkMembership(group, user.getId());
		return ResponseEntity.ok(groupService.getGroupSettlementStatus(groupId));
	}

	@Operation(summary = "Elimina un gruppo", description = "Elimina un gruppo. Solo l'admin può eseguire questa operazione. Con force=false, l'operazione fallisce se esistono debiti/crediti pendenti.")
	@ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Gruppo eliminato con successo"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "403", description = "L'utente non è admin del gruppo"),
			@ApiResponse(responseCode = "409", description = "Esistono debiti/crediti pendenti") })
	@DeleteMapping("/{groupId}")
	public ResponseEntity<String> deleteGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId,
			@RequestParam(defaultValue = "false") boolean force) {
		groupService.deleteGroup(groupId, force, user.getId());
		return ResponseEntity.ok("Gruppo eliminato");
	}

	private void checkMembership(Group group, Long userId) {
		if (group == null || !groupService.existsByGroupIdAndUserId(group.getId(), userId)) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
	}
}
