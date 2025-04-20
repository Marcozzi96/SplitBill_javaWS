package it.javaWS.javaws.services;

import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.repositories.GroupRepository;
import it.javaWS.javaws.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository, UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    public Group createGroup(String name, String description, Set<Long> userIds) {
        Set<User> users = new HashSet<>(userRepository.findAllById(userIds));

        Group group = new Group();
        group.setName(name);
        group.setDescription(description);
        group.setCreationDate(LocalDate.now());
        group.setUsers(users);

        return groupRepository.save(group);//
    }

    public Optional<Group> getGroup(Long id) {
        return groupRepository.findById(id);
    }
    
    public Group addUsersToGroup(Long groupId, Set<Long> userIds) {
        Group group = groupRepository.findById(groupId).orElseThrow();
        Set<User> usersToAdd = new HashSet<>(userRepository.findAllById(userIds));
        
        // Aggiungi utenti al set esistente
        group.getUsers().addAll(usersToAdd);
        return groupRepository.save(group);
    }
    
    public Group removeUsersFromGroup(Long groupId, Set<Long> userIds) {
        Group group = groupRepository.findById(groupId).orElseThrow();
        Set<User> usersToRemove = new HashSet<>(userRepository.findAllById(userIds));

        group.getUsers().removeAll(usersToRemove);
        return groupRepository.save(group);
    }


}
