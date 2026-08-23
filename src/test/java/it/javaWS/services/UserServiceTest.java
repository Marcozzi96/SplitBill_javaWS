package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.dto.UpdateUserRequest;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.DuplicateUserException;
import it.javaWS.utils.InvalidCredentialsException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipService friendshipService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User buildUser(Long id, String username, String email, String password) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setDeleted(false);
        return user;
    }

    @Test
    void createUser_success() {
        User user = buildUser(null, "mario", "mario@example.com", "pwd");
        when(userRepository.findByEmailOrUsernameIgnoreCase(user.getEmail(), user.getUsername()))
                .thenReturn(Collections.emptySet());
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.createUser(user);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void createUser_duplicate_returnsNull() {
        User user = buildUser(null, "mario", "mario@example.com", "pwd");
        when(userRepository.findByEmailOrUsernameIgnoreCase(user.getEmail(), user.getUsername()))
                .thenReturn(Set.of(new User()));

        User result = userService.createUser(user);

        assertThat(result).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_wrongOldPassword_throwsInvalidCredentials() {
        User user = buildUser(1L, "mario", "mario@example.com", "encodedPwd");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("wrong");

        when(passwordEncoder.matches("wrong", "encodedPwd")).thenReturn(false);

        assertThatThrownBy(() -> userService.updateUser(user, request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void updateUser_duplicateEmail_throwsDuplicateUser() {
        User user = buildUser(1L, "mario", "mario@example.com", "encodedPwd");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("oldPwd");
        request.setEmail("new@example.com");

        User other = buildUser(2L, "other", "new@example.com", "pwd");

        when(passwordEncoder.matches("oldPwd", "encodedPwd")).thenReturn(true);
        when(userRepository.findByEmailOrUsernameIgnoreCase("new@example.com", "new@example.com"))
                .thenReturn(Set.of(other));

        assertThatThrownBy(() -> userService.updateUser(user, request))
                .isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void updateUser_duplicateUsername_throwsDuplicateUser() {
        User user = buildUser(1L, "mario", "mario@example.com", "encodedPwd");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("oldPwd");
        request.setUsername("luigi");

        User other = buildUser(2L, "luigi", "other@example.com", "pwd");

        when(passwordEncoder.matches("oldPwd", "encodedPwd")).thenReturn(true);
        when(userRepository.findByEmailOrUsernameIgnoreCase("luigi", "luigi"))
                .thenReturn(Set.of(other));

        assertThatThrownBy(() -> userService.updateUser(user, request))
                .isInstanceOf(DuplicateUserException.class);
    }

    @Test
    void updateUser_changesPassword() {
        User user = buildUser(1L, "mario", "mario@example.com", "encodedPwd");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("oldPwd");
        request.setPassword("newPwd");

        when(passwordEncoder.matches("oldPwd", "encodedPwd")).thenReturn(true);
        when(passwordEncoder.encode("newPwd")).thenReturn("newEncodedPwd");
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.updateUser(user, request);

        assertThat(result.getPassword()).isEqualTo("newEncodedPwd");
    }

    @Test
    void updateUser_googleUserWithoutPassword_canSetFirstPassword() {
        User user = buildUser(1L, "mario", "mario@gmail.com", null);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("nuovaPwd");

        when(passwordEncoder.encode("nuovaPwd")).thenReturn("nuovaEncoded");
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.updateUser(user, request);

        assertThat(result.getPassword()).isEqualTo("nuovaEncoded");
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void updateUser_userWithPassword_missingOldPassword_throwsInvalidCredentials() {
        User user = buildUser(1L, "mario", "mario@example.com", "encodedPwd");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setPassword("nuovaPwd");

        assertThatThrownBy(() -> userService.updateUser(user, request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void updateUser_noChanges_returnsSavedUser() {
        User user = buildUser(1L, "mario", "mario@example.com", "encodedPwd");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setOldPassword("oldPwd");

        when(passwordEncoder.matches("oldPwd", "encodedPwd")).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.updateUser(user, request);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void loadUserByUsername_success() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findByUsernameIgnoreCase("mario")).thenReturn(Optional.of(user));

        assertThat(userService.loadUserByUsername("mario")).isEqualTo(user);
    }

    @Test
    void loadUserByUsername_notFound_throwsException() {
        when(userRepository.findByUsernameIgnoreCase("mario")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("mario"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void loadUserByEmailOrUsername_success() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findByEmailOrUsernameIgnoreCase("mario@example.com", "mario"))
                .thenReturn(Set.of(user));

        assertThat(userService.loadUserByEmailOrUsername("mario@example.com", "mario")).isEqualTo(user);
    }

    @Test
    void loadUserByEmailOrUsername_emptySet_throwsBadCredentials() {
        when(userRepository.findByEmailOrUsernameIgnoreCase("x", "x")).thenReturn(Collections.emptySet());

        assertThatThrownBy(() -> userService.loadUserByEmailOrUsername("x", "x"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loadUserByEmailOrUsername_multipleResults_throwsBadCredentials() {
        User u1 = buildUser(1L, "a", "a@example.com", "pwd");
        User u2 = buildUser(2L, "b", "b@example.com", "pwd");
        when(userRepository.findByEmailOrUsernameIgnoreCase("x", "x")).thenReturn(Set.of(u1, u2));

        assertThatThrownBy(() -> userService.loadUserByEmailOrUsername("x", "x"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void getByUsername_returnsUser() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findByUsernameIgnoreCase("mario")).thenReturn(Optional.of(user));

        assertThat(userService.getByUsername("mario")).isEqualTo(user);
    }

    @Test
    void existsByUsername_delegatesToRepository() {
        when(userRepository.existsByUsernameAndDeletedFalse("mario")).thenReturn(true);

        assertThat(userService.existsByUsername("mario")).isTrue();
    }

    @Test
    void existsByUsernameOrEmail_returnsTrueWhenFound() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findByEmailOrUsernameIgnoreCase("mario@example.com", "mario"))
                .thenReturn(Set.of(new User()));

        assertThat(userService.existsByUsernameOrEmail(user)).isTrue();
    }

    @Test
    void deleteUser_existing_returnsTrue() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.deleteUser(1L)).isTrue();
        verify(userRepository).deleteById(1L);
    }

    @Test
    void deleteUser_missing_returnsFalse() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThat(userService.deleteUser(1L)).isFalse();
    }

    @Test
    void inviaRichiestaAmicizia_self_throwsIllegalArgument() {
        assertThatThrownBy(() -> userService.inviaRichiestaAmicizia(1L, 1L, "ciao"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inviaRichiestaAmicizia_newRequest_success() throws Exception {
        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");
        User user2 = buildUser(2L, "luigi", "luigi@example.com", "pwd");

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(friendshipService.save(any(Friendship.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.inviaRichiestaAmicizia(1L, 2L, "ciao");

        verify(friendshipService).save(any(Friendship.class));
    }

    @Test
    void inviaRichiestaAmicizia_alreadyAccepted_throwsIllegalState() {
        Friendship friendship = new Friendship();
        friendship.setStato(StatoAmicizia.ACCETTATA);

        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");
        User user2 = buildUser(2L, "luigi", "luigi@example.com", "pwd");
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setUserToBeConfirmed(user2);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> userService.inviaRichiestaAmicizia(1L, 2L, "ciao"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accettaRichiestaAmicizia_success() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        User requester = buildUser(2L, "luigi", "luigi@example.com", "pwd");

        Friendship friendship = new Friendship();
        friendship.setUserToBeConfirmed(user);
        friendship.setStato(StatoAmicizia.IN_ATTESA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        when(friendshipService.save(friendship)).thenReturn(friendship);

        userService.accettaRichiestaAmicizia(user.getId(), requester.getId());

        assertThat(friendship.getStato()).isEqualTo(StatoAmicizia.ACCETTATA);
    }

    @Test
    void rifiutaRichiestaAmicizia_asReceiver_setsRifiutata() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        Friendship friendship = new Friendship();
        friendship.setUserToBeConfirmed(user);
        friendship.setStato(StatoAmicizia.IN_ATTESA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        when(friendshipService.save(friendship)).thenReturn(friendship);

        userService.rifiutaRichiestaAmicizia(1L, 2L);

        assertThat(friendship.getStato()).isEqualTo(StatoAmicizia.RIFIUTATA);
    }

    @Test
    void rifiutaRichiestaAmicizia_asSender_deletesFriendship() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        User other = buildUser(2L, "luigi", "luigi@example.com", "pwd");
        Friendship friendship = new Friendship();
        friendship.setUserToBeConfirmed(other);
        friendship.setStato(StatoAmicizia.IN_ATTESA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        userService.rifiutaRichiestaAmicizia(1L, 2L);

        verify(friendshipService).delete(friendship);
    }

    @Test
    void rimuoviAmico_success() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        User friend = buildUser(2L, "luigi", "luigi@example.com", "pwd");
        Friendship friendship = new Friendship();
        friendship.setUser1(user);
        friendship.setUser2(friend);
        friendship.setStato(StatoAmicizia.ACCETTATA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        userService.rimuoviAmico(1L, 2L);

        verify(friendshipService).delete(friendship);
    }

    @Test
    void getAmici_delegatesToFriendshipService() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(friendshipService.getFriendsOfUser(1L, StatoAmicizia.ACCETTATA)).thenReturn(List.of(user));

        assertThat(userService.getAmici(1L)).containsExactly(user);
    }

    @Test
    void getRichiesteAmiciziaInviate_delegatesToFriendshipService() {
        Friendship friendship = new Friendship();
        when(friendshipService.getSentFriendRequests(1L)).thenReturn(Set.of(friendship));

        assertThat(userService.getRichiesteAmiciziaInviate(1L)).containsExactly(friendship);
    }

    @Test
    void getRichiesteAmiciziaRicevute_delegatesToFriendshipService() {
        Friendship friendship = new Friendship();
        when(friendshipService.getReceivedFriendRequests(1L)).thenReturn(Set.of(friendship));

        assertThat(userService.getRichiesteAmiciziaRicevute(1L)).containsExactly(friendship);
    }

    @Test
    void getUser_delegatesToRepository() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThat(userService.getUser(1L)).isPresent().contains(user);
    }

    @Test
    void getAllUsers_delegatesToRepository() {
        User user = buildUser(1L, "mario", "mario@example.com", "pwd");
        when(userRepository.findAll()).thenReturn(List.of(user));

        assertThat(userService.getAllUsers()).containsExactly(user);
    }

    @Test
    void inviaRichiestaAmicizia_existingRejectedByOther_resetsRequest() throws Exception {
        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");
        User user2 = buildUser(2L, "luigi", "luigi@example.com", "pwd");

        Friendship friendship = new Friendship();
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setUserToBeConfirmed(user2);
        friendship.setStato(StatoAmicizia.RIFIUTATA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(friendshipService.save(friendship)).thenReturn(friendship);

        userService.inviaRichiestaAmicizia(2L, 1L, "riprovo");

        assertThat(friendship.getStato()).isEqualTo(StatoAmicizia.IN_ATTESA);
        assertThat(friendship.getUserToBeConfirmed()).isEqualTo(user1);
    }

    @Test
    void inviaRichiestaAmicizia_existingPendingAsRequester_throwsException() {
        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");
        User user2 = buildUser(2L, "luigi", "luigi@example.com", "pwd");

        Friendship friendship = new Friendship();
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setUserToBeConfirmed(user2);
        friendship.setStato(StatoAmicizia.IN_ATTESA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> userService.inviaRichiestaAmicizia(1L, 2L, "ciao"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void inviaRichiestaAmicizia_existingRejectedAsReceiver_throwsException() {
        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");
        User user2 = buildUser(2L, "luigi", "luigi@example.com", "pwd");

        Friendship friendship = new Friendship();
        friendship.setUser1(user1);
        friendship.setUser2(user2);
        friendship.setUserToBeConfirmed(user2);
        friendship.setStato(StatoAmicizia.RIFIUTATA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> userService.inviaRichiestaAmicizia(1L, 2L, "ciao"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accettaRichiestaAmicizia_wrongConfirmer_throwsException() {
        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");
        User user2 = buildUser(2L, "luigi", "luigi@example.com", "pwd");

        Friendship friendship = new Friendship();
        friendship.setUserToBeConfirmed(user2);
        friendship.setStato(StatoAmicizia.IN_ATTESA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> userService.accettaRichiestaAmicizia(1L, 2L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accettaRichiestaAmicizia_notPending_throwsException() {
        User user1 = buildUser(1L, "mario", "mario@example.com", "pwd");

        Friendship friendship = new Friendship();
        friendship.setUserToBeConfirmed(user1);
        friendship.setStato(StatoAmicizia.ACCETTATA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> userService.accettaRichiestaAmicizia(1L, 2L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rimuoviAmico_notAccepted_throwsException() {
        Friendship friendship = new Friendship();
        friendship.setStato(StatoAmicizia.IN_ATTESA);

        when(friendshipService.findFriendshipBetweenUsers(1L, 2L)).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> userService.rimuoviAmico(1L, 2L))
                .isInstanceOf(IllegalStateException.class);
    }
}
