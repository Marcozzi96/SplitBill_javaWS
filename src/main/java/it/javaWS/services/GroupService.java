package it.javaWS.services;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.GroupDTO;
import it.javaWS.models.dto.GroupMemberDTO;
import it.javaWS.models.dto.SettlementDTO;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.entities.UserGroupId;
import it.javaWS.models.enums.GroupRole;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.GroupNotFoundException;
import it.javaWS.utils.NotGroupAdminException;
import it.javaWS.utils.PendingSettlementsException;
import it.javaWS.utils.UserNotFoundException;

@Service
public class GroupService {

	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final UserGroupRepository userGroupRepository;
	private final BillRepository billRepository;
	private final TransactionRepository transactionRepository;
	private final BalanceService balanceService;

	public GroupService(GroupRepository groupRepository, UserRepository userRepository,
			UserGroupRepository userGroupRepository, BillRepository billRepository,
			TransactionRepository transactionRepository, BalanceService balanceService) {
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.userGroupRepository = userGroupRepository;
		this.billRepository = billRepository;
		this.transactionRepository = transactionRepository;
		this.balanceService = balanceService;
	}

	@Transactional
	public Group createGroup(String name, String description, Set<Long> userIds, Long creatorId) {

		// 1. Recupera gli utenti
		Set<User> users = new HashSet<>(userRepository.findAllById(userIds));

		// 2. Crea il gruppo
		Group group = new Group();
		group.setName(name);
		group.setDescription(description);
		group.setCreationDate(LocalDate.now());

		// 4. Crea relazioni UserGroup
		Set<UserGroup> userGroups = new HashSet<>();
		for (User user : users) {
			UserGroup userGroup = new UserGroup();
			userGroup.setUser(user);
			userGroup.setGroup(group);
			userGroup.setDataIngresso(LocalDate.now());
			userGroup.setDataUscita(null); // ancora attivo
			if (user.getId().equals(creatorId)) {
				userGroup.setRole(GroupRole.ADMIN);
			}
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
	public GroupDTO createGroupDto(String name, String description, Set<Long> userIds, Long creatorId) {
		Group group = createGroup(name, description, userIds, creatorId);
		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(getUsersInGroup(group.getId()));
		return dto;
	}

	@Transactional(readOnly = true)
	public Group getGroup(Long id) {
		Optional<Group> groupOpt = groupRepository.findById(id);
		return groupOpt.orElse(null);
	}

	@Transactional(readOnly = true)
	public GroupDTO getGroupDto(Long groupId, Long requestingUserId) {
		Group group = getGroup(groupId);
		if (group == null) {
			throw new GroupNotFoundException("Gruppo non trovato");
		}
		if (!existsByGroupIdAndUserId(groupId, requestingUserId)) {
			throw new org.springframework.security.access.AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
		GroupDTO dto = new GroupDTO(group);
		dto.setUsers(getUsersInGroup(groupId));
		return dto;
	}

	@Transactional
	public Group addUsersToGroup(Group group, Set<Long> userIds) {
		
		Set<User> usersToAdd = new HashSet<>(userRepository.findAllById(userIds));

		Set<UserGroup> userGroups = new HashSet<>();
		
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
	public GroupDTO addUsersToGroupDto(Long groupId, Set<Long> userIds, Long requestingUserId) {
		Group group = getGroup(groupId);
		if (group == null || !existsByGroupIdAndUserId(groupId, requestingUserId)) {
			throw new GroupNotFoundException("Gruppo non trovato");
		}
		Group updatedGroup = addUsersToGroup(group, userIds);
		GroupDTO dto = new GroupDTO(updatedGroup);
		dto.setUsers(getUsersInGroup(groupId));
		return dto;
	}

	@Transactional
	public Group removeUsersFromGroup(Long groupId, Set<Long> userIds) {

		Optional<Group> groupOpt = groupRepository.findById(groupId);
		if (groupOpt.isEmpty()) return null; // il gruppo non esiste
		Group group = groupOpt.get();

		Set<UserGroup> userGroupsToLeave = userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
		if (userGroupsToLeave.isEmpty()) return group;

		// Soft exit: popola dataUscita solo per membri attuali
		List<UserGroup> justLeft = userGroupsToLeave.stream()
				.filter(ug -> ug.getDataUscita() == null)
				.peek(ug -> ug.setDataUscita(LocalDate.now()))
				.toList();
		userGroupRepository.saveAll(userGroupsToLeave);

		List<UserGroup> activeMembers = userGroupRepository.findActiveByGroupId(groupId);

		if (activeMembers.isEmpty()) {
			deleteGroupWithContent(group);
			return null;
		}

		// I debiti/crediti degli uscenti si estinguono nel gruppo e passano a livello globale.
		for (UserGroup userGroup : justLeft) {
			balanceService.transferUserSettlementsToGlobal(group, userGroup.getUser().getId());
		}

		// Se tra gli uscenti c'era un admin, promuovi un altro membro attivo
		boolean adminLeft = userGroupsToLeave.stream().anyMatch(ug -> GroupRole.ADMIN.equals(ug.getRole()));
		if (adminLeft) {
			boolean stillAdminPresent = activeMembers.stream().anyMatch(ug -> GroupRole.ADMIN.equals(ug.getRole()));
			if (!stillAdminPresent) {
				UserGroup newAdmin = activeMembers.getFirst();
				newAdmin.setRole(GroupRole.ADMIN);
				userGroupRepository.save(newAdmin);
			}
		}

		return groupRepository.findById(groupId).orElseThrow();
	}

	@Transactional
	public GroupDTO removeUsersFromGroupDto(Long groupId, Set<Long> userIds, Long requestingUserId) {
		Group group = getGroup(groupId);
		if (group == null || !existsByGroupIdAndUserId(groupId, requestingUserId)) {
			throw new GroupNotFoundException("Gruppo non trovato");
		}
		Group updatedGroup = removeUsersFromGroup(groupId, userIds);
		if (updatedGroup == null) {
			return null;
		}
		GroupDTO dto = new GroupDTO(updatedGroup);
		dto.setUsers(getUsersInGroup(groupId));
		return dto;
	}

	private void deleteGroupWithContent(Group group) {
		balanceService.revertGroupBalances(group);
		List<Bill> bills = billRepository.findByGroupId(group.getId());
		for (Bill bill : bills) {
			List<Transaction> transactions = transactionRepository.findByBill_Id(bill.getId());
			transactionRepository.deleteAll(transactions);
		}
		billRepository.deleteAll(bills);
		groupRepository.delete(group);
	}
	
	
		
	@Transactional(readOnly = true)
	public List<Group> getGroupsByUserId(Long userId) {
		return groupRepository.getGroupsByUserId(userId);
	}

	@Transactional(readOnly = true)
	public Page<GroupDTO> getGroupsByUserIdDto(Long userId, Pageable pageable) {
		Page<Group> groups = groupRepository.getGroupsByUserId(userId, pageable);
		List<GroupDTO> dtos = groups.getContent().stream()
				.map(g -> new GroupDTO(g).setUsers(getUsersInGroup(g.getId())))
				.toList();
		return new PageImpl<>(dtos, pageable, groups.getTotalElements());
	}

	@Transactional
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
	    // Solo membri attivi: chi è uscito (dataUscita valorizzata) non compare più.
	    List<UserGroup> userGroups = userGroupRepository.findActiveByGroupId(groupId);

	    return userGroups.stream()
	            .map(UserGroup::getUser)
	            .collect(Collectors.toSet());
	}

	@Transactional(readOnly = true)
	public Set<UserDTO> getUsersInGroupDto(Long groupId) {
		return getUsersInGroup(groupId).stream()
				.map(UserDTO::new)
				.collect(Collectors.toSet());
	}
	
	@Transactional(readOnly = true)
    public Set<UserGroup> getUserGroup(Long groupId, Set<Long> userIds){
    	Set<UserGroup> userGroups = userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
    	// I chiamanti usano gli User fuori dalla sessione (es. BillController li inserisce
    	// in un Set: hashCode su proxy non inizializzato -> LazyInitializationException).
    	// Inizializzarli qui, finché la sessione è aperta.
    	userGroups.forEach(ug -> Hibernate.initialize(ug.getUser()));
    	return userGroups;
    }

    @Transactional(readOnly = true)
    public Set<UserGroup> getByUser(User user) {
        return userGroupRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public Set<UserGroup> getByGroup(Group group) {
        return userGroupRepository.findByGroup(group);
    }

    @Transactional
    public void deleteByGroupIdAndUserIds(Long groupId, Set<Long> userIds) {
        userGroupRepository.deleteByGroup_IdAndUser_IdIn(groupId, userIds);
    }

    @Transactional(readOnly = true)
    public Set<UserGroup> getByGroupIdAndUserIds(Long groupId, Set<Long> userIds) {
        return userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
    }

    @Transactional(readOnly = true)
    public boolean existsByGroupIdAndUserId(Long groupId, Long userId) {
        return userGroupRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isUserAdminOfGroup(Long groupId, Long userId) {
        return userGroupRepository.existsByGroupIdAndUserIdAndRole(groupId, userId, GroupRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UserGroup> getByGroupId(Long groupId) {
        return userGroupRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public Optional<UserGroup> getById(UserGroupId id) {
        return userGroupRepository.findById(id);
    }

    @Transactional
    public UserGroup save(UserGroup userGroup) {
        return userGroupRepository.save(userGroup);
    }

    @Transactional
    public void delete(UserGroup userGroup) {
        userGroupRepository.delete(userGroup);
    }

    @Transactional(readOnly = true)
    public List<GroupMemberDTO> getActiveMembers(Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Gruppo non trovato"));

        return userGroupRepository.findActiveByGroupId(groupId).stream()
                .map(GroupMemberDTO::new)
                .toList();
    }

    @Transactional
    public Group updateGroup(Long groupId, String name, String description, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Gruppo non trovato"));

        if (!isUserAdminOfGroup(groupId, userId)) {
            throw new NotGroupAdminException("Solo l'admin del gruppo può modificare il gruppo");
        }

        if (name != null && !name.isBlank()) {
            group.setName(name);
        }
        if (description != null) {
            group.setDescription(description);
        }

        return groupRepository.save(group);
    }

    @Transactional
    public GroupDTO updateGroupDto(Long groupId, String name, String description, Long userId) {
    	Group updatedGroup = updateGroup(groupId, name, description, userId);
    	GroupDTO dto = new GroupDTO(updatedGroup);
    	dto.setUsers(getUsersInGroup(groupId));
    	return dto;
    }

    @Transactional(readOnly = true)
    public List<SettlementDTO> getGroupSettlementStatus(Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Gruppo non trovato"));

        // I debiti pendenti si leggono dai settlement pairwise: sono netti di rimborsi
        // e dei trasferimenti a livello globale dovuti all'uscita di membri.
        return balanceService.getGroupSettlementStatus(groupId);
    }

    @Transactional
    public void deleteGroup(Long groupId, boolean force, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Gruppo non trovato"));

        if (!isUserAdminOfGroup(groupId, userId)) {
            throw new NotGroupAdminException("Solo l'admin del gruppo può eliminare il gruppo");
        }

        if (!force) {
            List<SettlementDTO> pendingSettlements = getGroupSettlementStatus(groupId);
            if (!pendingSettlements.isEmpty()) {
                throw new PendingSettlementsException("Esistono debiti/crediti pendenti nel gruppo: " + pendingSettlements);
            }
        }

        deleteGroupWithContent(group);
    }

}
