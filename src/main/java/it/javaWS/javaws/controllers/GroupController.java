package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.GroupDTO;
import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.services.GroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/groups")
public class GroupController {

	private final GroupService groupService;

	public GroupController(GroupService groupService) {
		this.groupService = groupService;
	}

	@PostMapping
	public GroupDTO createGroup(@RequestParam String name, @RequestParam String description,
			@RequestBody Set<Long> userIds) {
		return groupService.createGroup(name, description, userIds);
	}

	@GetMapping("/{id}")
	public GroupDTO getGroup(@PathVariable Long id) {
		return groupService.getGroup(id);
	}
	
	@GetMapping("/byUser/{id}")
	public List<GroupDTO> getGroupsByUserId(@PathVariable Long id){
		return groupService.getGroupsByUserId(id);
	}

	@PostMapping("/{groupId}/users")
	public GroupDTO addUsersToGroup(@PathVariable Long groupId, @RequestBody Set<Long> userIds) {
		return groupService.addUsersToGroup(groupId, userIds);
	}

	@DeleteMapping("/{groupId}/users")
	public GroupDTO removeUsersFromGroup(@PathVariable Long groupId, @RequestBody Set<Long> userIds) {
		return groupService.removeUsersFromGroup(groupId, userIds);
	}

	@DeleteMapping("/{id}")
	public void deleteGroup(@PathVariable Long id) {
		groupService.deleteGroup(id);
	}

}
