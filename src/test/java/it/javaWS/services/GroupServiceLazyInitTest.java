package it.javaWS.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.enums.GroupRole;
import it.javaWS.repositories.GroupRepository;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.repositories.UserRepository;

/**
 * Regressione per la LazyInitializationException su POST /bills/new:
 * il controller usa gli User restituiti da getUserGroup fuori dalla sessione
 * Hibernate (li inserisce in un Set: hashCode su proxy lazy -> eccezione).
 * Questo test NON è @Transactional, quindi riproduce lo scenario reale.
 */
@SpringBootTest
class GroupServiceLazyInitTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Test
    void getUserGroup_usersUsableOutsideHibernateSession() {
        User u1 = createUser("lazyuser1", "lazy1@example.com");
        User u2 = createUser("lazyuser2", "lazy2@example.com");
        Group group = new Group();
        group.setName("LazyGroup");
        group.setDescription("Test lazy init");
        group.setCreationDate(LocalDate.now());
        group = groupRepository.save(group);
        addMember(group, u1);
        addMember(group, u2);

        Set<UserGroup> userGroups = groupService.getUserGroup(group.getId(), Set.of(u1.getId(), u2.getId()));

        // Fuori dalla sessione: prima della fix questa riga lanciava LazyInitializationException.
        Set<User> users = userGroups.stream().map(UserGroup::getUser).collect(Collectors.toSet());

        assertEquals(2, users.size());
        users.forEach(u -> assertNotNull(u.getUsername()));
    }

    private User createUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("password");
        user.setRegDate(LocalDate.now());
        return userRepository.save(user);
    }

    private void addMember(Group group, User user) {
        UserGroup userGroup = new UserGroup();
        userGroup.setUser(user);
        userGroup.setGroup(group);
        userGroup.setDataIngresso(LocalDate.now());
        userGroup.setRole(GroupRole.MEMBER);
        userGroupRepository.save(userGroup);
    }
}
