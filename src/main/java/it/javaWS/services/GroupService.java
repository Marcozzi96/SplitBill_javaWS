package it.javaWS.services;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.entities.UserGroupId;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;

@Service
public class GroupService {

	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final UserGroupRepository userGroupRepository;
	

	public GroupService(GroupRepository groupRepository, UserRepository userRepository,
			UserGroupRepository userGroupRepository, FriendshipService friendshipService) {
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.userGroupRepository = userGroupRepository;
	}

	@Transactional
	public Group createGroup(String name, String description, Set<Long> userIds) {

		// 1. Recupera gli utenti
		Set<User> users = new HashSet<>(userRepository.findAllById(userIds));
		
		

		// 2. Crea il gruppo
		Group group = new Group();
		group.setName(name);
		group.setDescription(description);
		group.setCreationDate(LocalDate.now());

		// 4. Crea relazioni UserGroup
		Set<UserGroup> userGroups = new HashSet<UserGroup>();
		for (User user : users) {
			UserGroup userGroup = new UserGroup();
			userGroup.setUser(user);
			userGroup.setGroup(group);
			userGroup.setDataIngresso(LocalDate.now());
			userGroup.setDataUscita(null); // ancora attivo
			// Imposta esplicitamente l'id altrimenti il Set crede siano tutti elementi
			// uguali
			userGroup.setId(new UserGroupId(user.getId(), null)); // group.getId() ancora null
			userGroups.add(userGroup);
			// userGroupRepository.save(userGroup);
		}

		group.setUserGroups(userGroups);

		group = groupRepository.save(group);
//		GroupDTO dto = new GroupDTO(group);
//		dto.setUsers(group);
		return group;
	}

	@Transactional
	public Group getGroup(Long id) {
		Optional<Group> groupOpt = groupRepository.findById(id);
		return groupOpt.orElse(null);
	}
	
	@Transactional
	public Group addUsersToGroup(Group group, Set<Long> userIds) {
		
		Set<User> usersToAdd = new HashSet<>(userRepository.findAllById(userIds));

		Set<UserGroup> userGroups = new HashSet<UserGroup>();
		
//		Set<User> userToBe = group.getUsers();
		
		for (User user : usersToAdd) {
			Optional<UserGroup> userGroupOpt = userGroupRepository.findById(new UserGroupId(user.getId(), group.getId()));
			if(userGroupOpt.isPresent()) {
				userGroupOpt.get().setDataUscita(null);
				userGroupRepository.save(userGroupOpt.get());
			}else {
				UserGroup userGroup = new UserGroup();
				userGroup.setUser(user);
				userGroup.setGroup(group);
				userGroup.setDataIngresso(LocalDate.now());
				userGroup.setDataUscita(null); // ancora attivo
				
				userGroup.setId(new UserGroupId(user.getId(), null)); // group.getId() ancora null
				userGroups.add(userGroup);
			}
			
		}

		// Aggiungi utenti al set esistente
		//group.getUserGroups().addAll(userGroups);
		
		userGroupRepository.saveAll(userGroups);
		
		return getGroup(group.getId());

//		return new GroupDTO(group).setUsers(group);
	}

	@Transactional
	public Group removeUsersFromGroup(Long groupId, Set<Long> userIds) {

		Optional<Group> groupOpt = groupRepository.findById(groupId);
		if(groupOpt.isEmpty()) return null; //il gruppo non esiste
//		Set<UserGroup> userGroups = userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
//		
//		userGroups.forEach(ug->ug.setDataUscita(LocalDate.now()));
//		
//		userGroupRepository.saveAll(userGroups);
		
		userGroupRepository.deleteByGroup_IdAndUser_IdIn(groupId, userIds);
		
		
		return groupRepository.findById(groupId).orElseThrow();
		
	}
	
	
		
	public List<Group> getGroupsByUserId(Long userId) {
		return groupRepository.getGroupsByUserId(userId);
	}
	
	

	public Boolean deleteGroup(Long id) {
		Optional<Group> groupOpt = groupRepository.findById(id);
		if(groupOpt.isEmpty()) return false;
		groupRepository.deleteById(id);
		return true;
	}
	@Transactional(readOnly = true)
	public Boolean isUserInGroup(Long groupId, Long userId) {
	    return userGroupRepository.existsByGroupIdAndUserId(groupId, userId);
	}
	
	@Transactional(readOnly = true)
	public Set<User> getUsersInGroup(Long groupId) {
	    List<UserGroup> userGroups = userGroupRepository.findByGroupId(groupId);

	    return userGroups.stream()
	            .map(UserGroup::getUser)
	            .collect(Collectors.toSet());
	}
	
	public Set<UserGroup> getUserGroup(Long groupId, Set<Long> userIds){
		return userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
	}
	
    public Set<UserGroup> getByUser(User user) {
        return userGroupRepository.findByUser(user);
    }

    public Set<UserGroup> getByGroup(Group group) {
        return userGroupRepository.findByGroup(group);
    }

    @Transactional
    public void deleteByGroupIdAndUserIds(Long groupId, Set<Long> userIds) {
        userGroupRepository.deleteByGroup_IdAndUser_IdIn(groupId, userIds);
    }

    public Set<UserGroup> getByGroupIdAndUserIds(Long groupId, Set<Long> userIds) {
        return userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
    }

    public boolean existsByGroupIdAndUserId(Long groupId, Long userId) {
        return userGroupRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    public List<UserGroup> getByGroupId(Long groupId) {
        return userGroupRepository.findByGroupId(groupId);
    }

    public Optional<UserGroup> getById(UserGroupId id) {
        return userGroupRepository.findById(id);
    }

    public UserGroup save(UserGroup userGroup) {
        return userGroupRepository.save(userGroup);
    }

    public void delete(UserGroup userGroup) {
        userGroupRepository.delete(userGroup);
    }

}
