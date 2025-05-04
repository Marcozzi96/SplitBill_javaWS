package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.GroupDTO;
import it.javaWS.javaws.models.Group;
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
	private final JwtUtil jwtUtil;

	public GroupController(GroupService groupService, JwtUtil jwtUtil) {
		this.groupService = groupService;
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
		dto.setUsers(group);
		return ResponseEntity.ok(dto);
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getGroup(@RequestHeader("Authorization") String authHeader, @PathVariable Long id) {

		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long userId = jwtUtil.extractUserId(token);
		
		Group group = groupService.getGroup(id);
		
		if( group.getUsers().stream().map(u-> u.getId()).filter(i->i.equals(userId)).toList().size() != 1) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}

		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(group);
		dto.setBills(group);

		return ResponseEntity.ok(dto);
	}

	@GetMapping("/groups")
	public ResponseEntity<?> getGroupsByUser(@RequestHeader("Authorization") String authHeader) {
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
		Long userId = jwtUtil.extractUserId(token);
		
		return ResponseEntity.ok(groupService.getGroupsByUserId(userId).stream().map(g -> new GroupDTO(g)).toList());
	}

	@PostMapping("/{groupId}/users")
	public ResponseEntity<?> addUsersToGroup(@RequestHeader("Authorization") String authHeader, @PathVariable Long groupId, @RequestBody Set<Long> userIds) {
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long userId = jwtUtil.extractUserId(token);
		
		Group group = groupService.getGroup(groupId);
		
		if( group.getUsers().stream().map(u-> u.getId()).filter(i->i.equals(userId)).toList().size() != 1) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}
		
		GroupDTO groupDTO = new GroupDTO(group);
		groupDTO.setUsers(group);
		
		return ResponseEntity.ok(groupDTO);
	}

//	@DeleteMapping("/{groupId}/users")
//	public GroupDTO removeUsersFromGroup(@PathVariable Long groupId, @RequestBody Set<Long> userIds) {
//		return groupService.removeUsersFromGroup(groupId, userIds);
//	}

	//TODO: il gruppo può cancellarsi SOLO da solo quando tutti gli utenti escono
//	@DeleteMapping("/{id}")
//	public void deleteGroup(@PathVariable Long id) {
//		groupService.deleteGroup(id);
//	}
	
	//TODO: ESCI DAL GRUPPO
	@DeleteMapping("/leave")
	public ResponseEntity<?> leaveTheGroup(@RequestHeader("Authorization") String authHeader, @PathVariable Long groupId){
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		Long userId = jwtUtil.extractUserId(token);
		
		Group group = groupService.getGroup(groupId);
		
		if( group.getUsers().stream().map(u-> u.getId()).filter(i->i.equals(userId)).toList().size() != 1) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}
		
		Set<Long> userIds = new HashSet<Long>();
		userIds.add(userId);
		
		Group updatedGroup = groupService.removeUsersFromGroup(groupId, userIds);
		if(updatedGroup == null) 
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "Gruppo non trovato"));

		GroupDTO dto = new GroupDTO(updatedGroup).setUsers(updatedGroup);
		
		return ResponseEntity.ok(dto);
	}

}
