package it.javaWS.javaws.controllers;

import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.services.GroupService;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public Group createGroup(@RequestParam String name,
                             @RequestParam String description,
                             @RequestBody Set<Long> userIds) {
        return groupService.createGroup(name, description, userIds);
    }

    @GetMapping("/{id}")
    public Group getGroup(@PathVariable Long id) {
        return groupService.getGroup(id).orElseThrow();
    }
    
    @PostMapping("/{groupId}/users")
    public Group addUsersToGroup(@PathVariable Long groupId,
                                 @RequestBody Set<Long> userIds) {
        return groupService.addUsersToGroup(groupId, userIds);
    }

    @DeleteMapping("/{groupId}/users")
    public Group removeUsersFromGroup(@PathVariable Long groupId,
                                      @RequestBody Set<Long> userIds) {
        return groupService.removeUsersFromGroup(groupId, userIds);
    }

}
