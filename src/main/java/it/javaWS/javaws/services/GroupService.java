package it.javaWS.javaws.services;

import it.javaWS.javaws.dto.GroupDTO;
import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.models.UserGroup;
import it.javaWS.javaws.models.UserGroupId;
import it.javaWS.javaws.repositories.GroupRepository;
import it.javaWS.javaws.repositories.UserGroupRepository;
import it.javaWS.javaws.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GroupService {

	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final UserGroupRepository userGroupRepository;

	public GroupService(GroupRepository groupRepository, UserRepository userRepository,
			UserGroupRepository userGroupRepository) {
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.userGroupRepository = userGroupRepository;
	}

	@Transactional
	public GroupDTO createGroup(String name, String description, Set<Long> userIds) {

		// 1. Recupera gli utenti
		Set<User> users = new HashSet<>(userRepository.findAllById(userIds));

		if (users.size() != userIds.size()) {
			throw new IllegalArgumentException("Alcuni utenti non esistono");
		}

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
		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(group);
		return dto;
	}

	@Transactional
	public GroupDTO getGroup(Long id) {
		Optional<Group> groupOpt = groupRepository.findById(id);
		if (groupOpt.isEmpty())
			return null;

		GroupDTO groupDTO = new GroupDTO(groupOpt.get());
		groupDTO.setUsers(groupOpt.get());
		return groupDTO;
	}
	
	@Transactional
	public GroupDTO addUsersToGroup(Long groupId, Set<Long> userIds) {
		Group group = groupRepository.findById(groupId).orElseThrow();
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
		group.getUserGroups().addAll(userGroups);
		groupRepository.save(group);

		return new GroupDTO(group).setUsers(group);
	}

	@Transactional
	public GroupDTO removeUsersFromGroup(Long groupId, Set<Long> userIds) {

		for (Long userId : userIds) {
			UserGroupId userGroupId = new UserGroupId(userId, groupId);
			UserGroup userGroup = userGroupRepository.findById(userGroupId).get();
			userGroup.setDataUscita(LocalDate.now());
			userGroupRepository.save(userGroup);
		}

//        group.getUserGroups().stream().filter(x->x.getUser().getId().equals(userId))
//        	.forEach(x->{
//        		x.setDataUscita(LocalDate.now());
//        		userGroupRepository.save(x);
//        	});
		Group group = groupRepository.findById(groupId).orElseThrow();
		return new GroupDTO(group).setUsers(group);
	}

	public void deleteGroup(Long id) {
		groupRepository.deleteById(id);
	}

}
