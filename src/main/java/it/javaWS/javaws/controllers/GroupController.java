package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.GroupDTO;
import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.repositories.UserGroupRepository;
import it.javaWS.javaws.security.JwtUtil;
import it.javaWS.javaws.services.GroupService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/groups")
@PreAuthorize("isAuthenticated()")
public class GroupController {

	private final GroupService groupService;
	private final UserGroupRepository userGroupRepository;
	private final JwtUtil jwtUtil;

	public GroupController(GroupService groupService, JwtUtil jwtUtil, UserGroupRepository userGroupRepository) {
		this.groupService = groupService;
		this.userGroupRepository = userGroupRepository;
		this.jwtUtil = jwtUtil;
	}

	@PostMapping("/create")
	public ResponseEntity<?> createGroup(@RequestHeader("Authorization") String authHeader, @RequestParam String name,
			@RequestParam String description, @RequestBody Set<Long> userIds) {

		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long creatorId = jwtUtil.extractUserId(token);

		// TODO: è da verificare che gli users da inserire nel gruppo siano
		// amici/conoscenti del creator.

		userIds.add(creatorId);

		Group group = groupService.createGroup(name, description, userIds);
		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(groupService.getUsersInGroup(group.getId()));
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/{groupId}")
	public ResponseEntity<?> getGroup(@RequestHeader("Authorization") String authHeader, @PathVariable Long groupId) {

		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long userId = jwtUtil.extractUserId(token);

		Group group = groupService.getGroup(groupId);

		if (group == null || !userGroupRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}

		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(groupService.getUsersInGroup(groupId));
		//dto.setBills(group);//TODO

		return ResponseEntity.ok(dto);
	}

	@GetMapping("")
	public ResponseEntity<?> getGroupsByUser(@RequestHeader("Authorization") String authHeader) {
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
		Long userId = jwtUtil.extractUserId(token);

		return ResponseEntity.ok(groupService.getGroupsByUserId(userId).stream().map(g -> new GroupDTO(g)).toList());
	}

	@PostMapping("/addUsers/{groupId}")
	public ResponseEntity<?> addUsersToGroup(@RequestHeader("Authorization") String authHeader,
			@PathVariable Long groupId, @RequestBody Set<Long> userIds) {
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long userId = jwtUtil.extractUserId(token);

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

//	@DeleteMapping("/{groupId}/users")
//	public GroupDTO removeUsersFromGroup(@PathVariable Long groupId, @RequestBody Set<Long> userIds) {
//		return groupService.removeUsersFromGroup(groupId, userIds);
//	}

	// TODO: il gruppo può cancellarsi SOLO da solo quando tutti gli utenti escono
//	@DeleteMapping("/{id}")
//	public void deleteGroup(@PathVariable Long id) {
//		groupService.deleteGroup(id);
//	}

	// TODO: ESCI DAL GRUPPO
	@DeleteMapping("/leave/{groupId}")
	public ResponseEntity<?> leaveTheGroup(@RequestHeader("Authorization") String authHeader,
			@PathVariable Long groupId) {
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long userId = jwtUtil.extractUserId(token);

		Group group = groupService.getGroup(groupId);

		if (group == null || !userGroupRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}

		Set<Long> userIds = new HashSet<Long>();
		userIds.add(userId);

		Group updatedGroup = groupService.removeUsersFromGroup(groupId, userIds);
		if (updatedGroup == null)
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Gruppo non trovato"));

		GroupDTO dto = new GroupDTO(updatedGroup).setUsers(groupService.getUsersInGroup(groupId));

		return ResponseEntity.ok(dto);
	}

}
