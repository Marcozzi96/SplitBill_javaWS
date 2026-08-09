package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

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
}
