package it.javaWS.javaws.services;

import it.javaWS.javaws.enums.StatoAmicizia;
import it.javaWS.javaws.models.Friendship;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.repositories.FriendshipRepository;
import it.javaWS.javaws.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService{

	private final UserRepository userRepository;
	private final FriendshipRepository friendshipRepository;

	public UserService(UserRepository userRepository, FriendshipRepository friendshipRepository) {
		this.userRepository = userRepository;
		this.friendshipRepository = friendshipRepository;
	}

	@Transactional
	public User createUser(User user) {

		if (userRepository.findByEmailOrUsername(user.getEmail(), user.getUsername()).size() > 0)
			return null; // Username o Email già utilizzati
		return userRepository.save(user);
	}

	@Transactional
	public Optional<User> getUser(Long id) {
		return userRepository.findById(id);
	}

	@Transactional
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}

	@Transactional
	public Boolean deleteUser(Long id) {
		if (!getUser(id).isPresent())
			return false;
		userRepository.deleteById(id);
		return true;
	}

	@Transactional
	public User updateUser(User user) {
		return userRepository.save(user);
	}

	@Override
    public User loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
        		.orElseThrow(() -> new UsernameNotFoundException("Credenziali non valide"));
    }
	
	public User getByUsername(String username) {
		return userRepository.findByUsername(username)
        		.orElse(null);
	}
	
	public Boolean existsByUsername(String username) {
		return userRepository.existsByUsername(username);
	}

	public void inviaRichiestaAmicizia(Long userId, Long otherId) {
	    if (userId.equals(otherId)) throw new IllegalArgumentException("Non puoi aggiungere te stesso");

	    // Ordinamento per garantire utente1 < utente2
	    Long user1Id = Math.min(userId, otherId);
	    Long user2Id = Math.max(userId, otherId);

	    Optional<Friendship> existing = friendshipRepository.findBetweenUsers(user1Id, user2Id);
	    if (existing.isPresent()) throw new IllegalStateException("Amicizia già esistente o in attesa");

	    User user1 = userRepository.findById(user1Id)
	        .orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));
	    User user2 = userRepository.findById(user2Id)
	        .orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));

	    Friendship friendship = new Friendship();
	    friendship.setUtente1(user1);
	    friendship.setUtente2(user2);
	    friendship.setStato(StatoAmicizia.IN_ATTESA);
	    friendship.setDataRichiesta(LocalDateTime.now());

	    friendshipRepository.save(friendship);
	}
    
    public void accettaRichiestaAmicizia(Long userId, Long requesterId) {
        Friendship friendship = friendshipRepository.findBetweenUsers(userId, requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Richiesta non trovata"));

        if (friendship.getStato() != StatoAmicizia.IN_ATTESA) {
            throw new IllegalStateException("La richiesta non è in attesa");
        }

        friendship.setStato(StatoAmicizia.ACCETTATA);
        friendshipRepository.save(friendship);
    }
    public void rifiutaRichiestaAmicizia(Long userId, Long requesterId) {
        Friendship friendship = friendshipRepository.findBetweenUsers(userId, requesterId)
            .orElseThrow(() -> new EntityNotFoundException("Richiesta non trovata"));

        friendship.setStato(StatoAmicizia.RIFIUTATA);
        friendshipRepository.save(friendship);
    }

    public void rimuoviAmico(Long userId, Long friendId) {
        Friendship friendship = friendshipRepository.findBetweenUsers(userId, friendId)
            .orElseThrow(() -> new EntityNotFoundException("Amicizia non trovata"));

        friendshipRepository.delete(friendship);
    }

    public List<User> getAmici(Long userId) {
        return friendshipRepository.findFriendsOfUser(userId);
    }
}
