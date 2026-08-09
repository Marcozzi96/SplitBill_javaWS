package it.javaWS.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class GroupService {

	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final UserGroupRepository userGroupRepository;
	private final BillRepository billRepository;
	private final TransactionRepository transactionRepository;

	public GroupService(GroupRepository groupRepository, UserRepository userRepository,
			UserGroupRepository userGroupRepository, BillRepository billRepository,
			TransactionRepository transactionRepository) {
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.userGroupRepository = userGroupRepository;
		this.billRepository = billRepository;
		this.transactionRepository = transactionRepository;
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

	@Transactional(readOnly = true)
	public Group getGroup(Long id) {
		Optional<Group> groupOpt = groupRepository.findById(id);
		return groupOpt.orElse(null);
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
	public Group removeUsersFromGroup(Long groupId, Set<Long> userIds) {

		Optional<Group> groupOpt = groupRepository.findById(groupId);
		if (groupOpt.isEmpty()) return null; // il gruppo non esiste
		Group group = groupOpt.get();

		Set<UserGroup> userGroupsToLeave = userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
		if (userGroupsToLeave.isEmpty()) return group;

		// Soft exit: popola dataUscita solo per membri attuali
		userGroupsToLeave.stream()
				.filter(ug -> ug.getDataUscita() == null)
				.forEach(ug -> ug.setDataUscita(LocalDate.now()));
		userGroupRepository.saveAll(userGroupsToLeave);

		List<UserGroup> activeMembers = userGroupRepository.findActiveByGroupId(groupId);

		if (activeMembers.isEmpty()) {
			deleteGroupWithContent(group);
			return null;
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

	private void deleteGroupWithContent(Group group) {
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
	    List<UserGroup> userGroups = userGroupRepository.findByGroupId(groupId);

	    return userGroups.stream()
	            .map(UserGroup::getUser)
	            .collect(Collectors.toSet());
	}
	
	@Transactional(readOnly = true)
	public Set<UserGroup> getUserGroup(Long groupId, Set<Long> userIds){
		return userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, userIds);
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

    @Transactional(readOnly = true)
    public List<SettlementDTO> getGroupSettlementStatus(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Gruppo non trovato"));

        Map<String, SettlementDTO> settlements = new HashMap<>();

        for (Bill bill : billRepository.findByGroupId(groupId)) {
            User buyer = bill.getBuyer();
            for (Transaction transaction : transactionRepository.findByBill_Id(bill.getId())) {
                User debtor = transaction.getUser();
                BigDecimal amount = transaction.getAmount().negate();

                if (debtor.getId().equals(buyer.getId()) || amount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                String key = debtor.getId() + "->" + buyer.getId();
                settlements.merge(key,
                        new SettlementDTO(new UserDTO(debtor), new UserDTO(buyer), amount),
                        (existing, newSettlement) -> {
                            existing.setAmount(existing.getAmount().add(newSettlement.getAmount()));
                            return existing;
                        });
            }
        }

        return new ArrayList<>(settlements.values());
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
