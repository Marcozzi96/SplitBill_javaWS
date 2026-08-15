package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.javaWS.models.dto.GroupMemberDTO;
import it.javaWS.models.dto.SettlementDTO;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.entities.Group;
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

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private BillRepository billRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private GroupService groupService;

    @Test
    void createGroup_assignsAdminToCreator() {
        User creator = new User();
        creator.setId(1L);
        creator.setUsername("creator");

        User member = new User();
        member.setId(2L);
        member.setUsername("member");

        Set<Long> userIds = new HashSet<>();
        userIds.add(creator.getId());
        userIds.add(member.getId());

        when(userRepository.findAllById(userIds)).thenReturn(java.util.List.of(creator, member));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group group = invocation.getArgument(0);
            group.setId(10L);
            return group;
        });

        Group group = groupService.createGroup("Trip", "Test trip", userIds, creator.getId());

        assertThat(group.getUserGroups()).hasSize(2);
        assertThat(group.getUserGroups())
                .anyMatch(ug -> ug.getUser().getId().equals(creator.getId()) && ug.getRole() == GroupRole.ADMIN)
                .anyMatch(ug -> ug.getUser().getId().equals(member.getId()) && ug.getRole() == GroupRole.MEMBER);
    }

    @Test
    void isUserAdminOfGroup_delegatesToRepository() {
        when(userGroupRepository.existsByGroupIdAndUserIdAndRole(10L, 1L, GroupRole.ADMIN)).thenReturn(true);

        boolean result = groupService.isUserAdminOfGroup(10L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    void removeUsersFromGroup_lastActiveMember_deletesGroup() {
        Group group = new Group();
        group.setId(1L);

        User user = new User();
        user.setId(1L);

        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setRole(GroupRole.ADMIN);
        userGroup.setDataUscita(null);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.findByGroup_IdAndUser_IdIn(1L, Set.of(1L))).thenReturn(Set.of(userGroup));
        when(userGroupRepository.findActiveByGroupId(1L)).thenReturn(List.of());
        when(billRepository.findByGroupId(1L)).thenReturn(List.of());

        Group result = groupService.removeUsersFromGroup(1L, Set.of(1L));

        assertThat(result).isNull();
        verify(groupRepository).delete(group);
    }

    @Test
    void removeUsersFromGroup_adminLeaves_promotesAnotherMember() {
        Group group = new Group();
        group.setId(1L);

        User admin = new User();
        admin.setId(1L);

        User member = new User();
        member.setId(2L);

        UserGroup adminGroup = new UserGroup();
        adminGroup.setUser(admin);
        adminGroup.setGroup(group);
        adminGroup.setRole(GroupRole.ADMIN);
        adminGroup.setDataUscita(null);

        UserGroup memberGroup = new UserGroup();
        memberGroup.setUser(member);
        memberGroup.setGroup(group);
        memberGroup.setRole(GroupRole.MEMBER);
        memberGroup.setDataUscita(null);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.findByGroup_IdAndUser_IdIn(1L, Set.of(1L))).thenReturn(Set.of(adminGroup));
        when(userGroupRepository.findActiveByGroupId(1L)).thenReturn(List.of(memberGroup));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        groupService.removeUsersFromGroup(1L, Set.of(1L));

        assertThat(memberGroup.getRole()).isEqualTo(GroupRole.ADMIN);
        verify(userGroupRepository).save(memberGroup);
    }

    @Test
    void removeUsersFromGroup_memberLeaves_transfersSettlementsToGlobal() {
        Group group = new Group();
        group.setId(1L);

        User admin = new User();
        admin.setId(1L);

        User member = new User();
        member.setId(2L);

        UserGroup adminGroup = new UserGroup();
        adminGroup.setUser(admin);
        adminGroup.setGroup(group);
        adminGroup.setRole(GroupRole.ADMIN);
        adminGroup.setDataUscita(null);

        UserGroup memberGroup = new UserGroup();
        memberGroup.setUser(member);
        memberGroup.setGroup(group);
        memberGroup.setRole(GroupRole.MEMBER);
        memberGroup.setDataUscita(null);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.findByGroup_IdAndUser_IdIn(1L, Set.of(2L))).thenReturn(Set.of(memberGroup));
        when(userGroupRepository.findActiveByGroupId(1L)).thenReturn(List.of(adminGroup));

        groupService.removeUsersFromGroup(1L, Set.of(2L));

        // All'uscita i debiti/crediti del membro passano dallo scope gruppo a quello globale.
        verify(balanceService).transferUserSettlementsToGlobal(group, 2L);
    }

    @Test
    void removeUsersFromGroup_lastMember_deletesGroupWithoutTransfer() {
        Group group = new Group();
        group.setId(1L);

        User user = new User();
        user.setId(1L);

        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setRole(GroupRole.ADMIN);
        userGroup.setDataUscita(null);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.findByGroup_IdAndUser_IdIn(1L, Set.of(1L))).thenReturn(Set.of(userGroup));
        when(userGroupRepository.findActiveByGroupId(1L)).thenReturn(List.of());
        when(billRepository.findByGroupId(1L)).thenReturn(List.of());

        groupService.removeUsersFromGroup(1L, Set.of(1L));

        // Il gruppo viene eliminato: nessun trasferimento, i dati vengono rimossi.
        verify(balanceService, never()).transferUserSettlementsToGlobal(any(), any());
        verify(groupRepository).delete(group);
    }

    @Test
    void updateGroup_notAdmin_throwsException() {
        Group group = new Group();
        group.setId(1L);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.existsByGroupIdAndUserIdAndRole(1L, 2L, GroupRole.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> groupService.updateGroup(1L, "New Name", null, 2L))
                .isInstanceOf(NotGroupAdminException.class);
    }

    @Test
    void updateGroup_admin_updatesNameAndDescription() {
        Group group = new Group();
        group.setId(1L);
        group.setName("Old Name");
        group.setDescription("Old Description");

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.existsByGroupIdAndUserIdAndRole(1L, 1L, GroupRole.ADMIN)).thenReturn(true);
        when(groupRepository.save(group)).thenReturn(group);

        Group updated = groupService.updateGroup(1L, "New Name", "New Description", 1L);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getDescription()).isEqualTo("New Description");
    }

    @Test
    void deleteGroup_notAdmin_throwsException() {
        Group group = new Group();
        group.setId(1L);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.existsByGroupIdAndUserIdAndRole(1L, 2L, GroupRole.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> groupService.deleteGroup(1L, false, 2L))
                .isInstanceOf(NotGroupAdminException.class);
    }

    @Test
    void deleteGroup_withPendingSettlementsWithoutForce_throwsException() {
        Group group = new Group();
        group.setId(1L);

        User buyer = new User();
        buyer.setId(1L);
        buyer.setUsername("buyer");
        User debtor = new User();
        debtor.setId(2L);
        debtor.setUsername("debtor");
        SettlementDTO pending = new SettlementDTO(new UserDTO(debtor), new UserDTO(buyer),
                new BigDecimal("50"));

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.existsByGroupIdAndUserIdAndRole(1L, 1L, GroupRole.ADMIN)).thenReturn(true);
        when(balanceService.getGroupSettlementStatus(1L)).thenReturn(List.of(pending));

        assertThatThrownBy(() -> groupService.deleteGroup(1L, false, 1L))
                .isInstanceOf(PendingSettlementsException.class);
    }

    @Test
    void deleteGroup_withPendingSettlementsWithForce_deletesGroup() {
        Group group = new Group();
        group.setId(1L);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.existsByGroupIdAndUserIdAndRole(1L, 1L, GroupRole.ADMIN)).thenReturn(true);
        when(billRepository.findByGroupId(1L)).thenReturn(List.of());

        groupService.deleteGroup(1L, true, 1L);

        verify(groupRepository).delete(group);
    }

    @Test
    void getGroupSettlementStatus_returnsPendingSettlements() {
        Group group = new Group();
        group.setId(1L);

        User buyer = new User();
        buyer.setId(1L);
        buyer.setUsername("buyer");

        User debtor = new User();
        debtor.setId(2L);
        debtor.setUsername("debtor");

        SettlementDTO pending = new SettlementDTO(new UserDTO(debtor), new UserDTO(buyer), new BigDecimal("50"));

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(balanceService.getGroupSettlementStatus(1L)).thenReturn(List.of(pending));

        List<SettlementDTO> settlements = groupService.getGroupSettlementStatus(1L);

        assertThat(settlements).hasSize(1);
        assertThat(settlements.getFirst().getDebtor().getUserId()).isEqualTo(2L);
        assertThat(settlements.getFirst().getCreditor().getUserId()).isEqualTo(1L);
        assertThat(settlements.getFirst().getAmount()).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    void getGroupSettlementStatus_groupNotFound_throwsException() {
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getGroupSettlementStatus(1L))
                .isInstanceOf(GroupNotFoundException.class);
    }

    @Test
    void getGroup_returnsGroup() {
        Group group = new Group();
        group.setId(1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        assertThat(groupService.getGroup(1L)).isEqualTo(group);
    }

    @Test
    void getGroup_notFound_returnsNull() {
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(groupService.getGroup(1L)).isNull();
    }

    @Test
    void addUsersToGroup_newMember_addsUserGroup() {
        Group group = new Group();
        group.setId(1L);

        User user = new User();
        user.setId(2L);

        when(userRepository.findAllById(Set.of(2L))).thenReturn(List.of(user));
        when(userGroupRepository.findById(new UserGroupId(2L, 1L))).thenReturn(Optional.empty());
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        Group result = groupService.addUsersToGroup(group, Set.of(2L));

        assertThat(result).isEqualTo(group);
        verify(userGroupRepository).saveAll(any());
    }

    @Test
    void addUsersToGroup_existingMember_resetsDataUscita() {
        Group group = new Group();
        group.setId(1L);

        User user = new User();
        user.setId(2L);

        UserGroup existing = new UserGroup();
        existing.setUser(user);
        existing.setGroup(group);
        existing.setDataUscita(LocalDate.now());

        when(userRepository.findAllById(Set.of(2L))).thenReturn(List.of(user));
        when(userGroupRepository.findById(new UserGroupId(2L, 1L))).thenReturn(Optional.of(existing));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        Group result = groupService.addUsersToGroup(group, Set.of(2L));

        assertThat(existing.getDataUscita()).isNull();
        verify(userGroupRepository).save(existing);
    }

    @Test
    void getActiveMembers_returnsMemberDtos() {
        Group group = new Group();
        group.setId(1L);

        User user = new User();
        user.setId(2L);
        user.setUsername("mario");
        user.setEmail("mario@example.com");

        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setRole(GroupRole.MEMBER);
        userGroup.setDataIngresso(LocalDate.now());

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userGroupRepository.findActiveByGroupId(1L)).thenReturn(List.of(userGroup));

        List<GroupMemberDTO> members = groupService.getActiveMembers(1L);

        assertThat(members).hasSize(1);
        assertThat(members.getFirst().getUserId()).isEqualTo(2L);
    }

    @Test
    void getUsersInGroup_returnsUsers() {
        Group group = new Group();
        group.setId(1L);

        User user = new User();
        user.setId(2L);

        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);

        when(userGroupRepository.findActiveByGroupId(1L)).thenReturn(List.of(userGroup));

        Set<User> users = groupService.getUsersInGroup(1L);

        assertThat(users).containsExactly(user);
    }

    @Test
    void getGroupsByUserId_delegatesToRepository() {
        Group group = new Group();
        group.setId(1L);
        when(groupRepository.getGroupsByUserId(2L)).thenReturn(List.of(group));

        assertThat(groupService.getGroupsByUserId(2L)).containsExactly(group);
    }

    @Test
    void deleteGroup_existing_deletesAndReturnsTrue() {
        Group group = new Group();
        group.setId(1L);
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        assertThat(groupService.deleteGroup(1L)).isTrue();
        verify(groupRepository).deleteById(1L);
    }

    @Test
    void deleteGroup_missing_returnsFalse() {
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(groupService.deleteGroup(1L)).isFalse();
    }

    @Test
    void isUserInGroup_delegatesToRepository() {
        when(userGroupRepository.existsByGroupIdAndUserId(1L, 2L)).thenReturn(true);

        assertThat(groupService.isUserInGroup(1L, 2L)).isTrue();
    }

    @Test
    void existsByGroupIdAndUserId_delegatesToRepository() {
        when(userGroupRepository.existsByGroupIdAndUserId(1L, 2L)).thenReturn(true);

        assertThat(groupService.existsByGroupIdAndUserId(1L, 2L)).isTrue();
    }
}
