package it.javaWS.services;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserService implements UserDetailsService {

	private final UserRepository userRepository;
	private final FriendshipService friendshipService;

	public UserService(UserRepository userRepository, FriendshipService friendshipService) {
		this.userRepository = userRepository;
		this.friendshipService = friendshipService;
	}

	@Transactional
	public User createUser(User user) {

		if (userRepository.findByEmailOrUsernameIgnoreCase(user.getEmail(), user.getUsername()).size() > 0)
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
		return userRepository.findByUsernameIgnoreCase(username)
				.orElseThrow(() -> new UsernameNotFoundException("Credenziali non valide"));
	}

	public User loadUserByEmailOrUsername(String email, String username) {
		Set<User> users = userRepository.findByEmailOrUsernameIgnoreCase(email, username);
		if (users.size() != 1) {
			throw new IllegalStateException("Credenziali non valide");
		}

		return users.stream().findFirst().get();
	}

	public User getByUsername(String username) {
		return userRepository.findByUsernameIgnoreCase(username).orElse(null);
	}

	public Boolean existsByUsername(String username) {
		return userRepository.existsByUsername(username);
	}

	public Boolean existsByUsernameOrEmail(User user) {
		if (userRepository.findByEmailOrUsernameIgnoreCase(user.getEmail(), user.getUsername()).size() > 0)
			return true; // Username o Email già utilizzati
		return false;
	}

	public void inviaRichiestaAmicizia(Long userId, Long otherId) throws Exception {
		if (userId.equals(otherId))
			throw new IllegalArgumentException("Non puoi aggiungere te stesso");

		// Ordinamento per garantire utente1 < utente2
		Long user1Id = Math.min(userId, otherId);
		Long user2Id = Math.max(userId, otherId);

		Optional<Friendship> existing = friendshipService.findFriendshipBetweenUsers(user1Id, user2Id);
		if (existing.isPresent()) { // Esiste la riga
			if (existing.get().getUserToBeConfirmed().getId() == userId) { // l'utente che deve confermare è lo stesso
																			// che prova a fare richiesta
				if (existing.get().getStato().equals(StatoAmicizia.IN_ATTESA)) {
					throw new IllegalStateException("Amicizia già in attesa di conferma");
				} else if (existing.get().getStato().equals(StatoAmicizia.ACCETTATA)) {
					throw new IllegalStateException("Siete già amici");
				} else if (existing.get().getStato().equals(StatoAmicizia.RIFIUTATA)) { // Devo aggiornare la riga
																						// esistente
					Friendship f = existing.get();
					User userToBeConfirmed = userRepository.findById(user2Id)
							.orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));
					f.setUserToBeConfirmed(userToBeConfirmed); // cambiare l'utente che deve accettare
					f.setStato(StatoAmicizia.IN_ATTESA); // Rimettere la richiesta in attesa
					friendshipService.save(f);
					return;
				}
			}
			/// l'utente che fa richiesta (userId) NON è chi deve confermare
			switch (existing.get().getStato()) {
			case StatoAmicizia.ACCETTATA:
				throw new IllegalStateException("Siete già amici");
			case StatoAmicizia.IN_ATTESA:
				throw new IllegalStateException("Amicizia già in attesa di conferma");
			case StatoAmicizia.RIFIUTATA:
				throw new IllegalStateException("Richiesta di amicizia rifutata. Se è tuo amico, può inviarti lui la richiesta.");
			default:
				throw new Exception("Errore generico");
			}
		}

		Friendship friendship = new Friendship();
		User user1 = userRepository.findById(user1Id)
				.orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));
		User user2 = userRepository.findById(user2Id)
				.orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));

		friendship.setUser1(user1);
		friendship.setUser2(user2);
		friendship.setUserToBeConfirmed(user1.getId().equals(otherId) ? user1 : user2);
		friendship.setStato(StatoAmicizia.IN_ATTESA);
		friendship.setDataRichiesta(LocalDateTime.now());

		friendshipService.save(friendship);
	}

	public void accettaRichiestaAmicizia(Long userId, Long requesterId) {
		Friendship friendship = friendshipService.findFriendshipBetweenUsers(userId, requesterId)
				.orElseThrow(() -> new EntityNotFoundException("Richiesta non trovata"));

		if (friendship.getStato() != StatoAmicizia.IN_ATTESA) {
			throw new IllegalStateException("La richiesta non è in attesa");
		}

		friendship.setStato(StatoAmicizia.ACCETTATA);
		friendshipService.save(friendship);
	}

	public Set<Friendship> getRichiesteAmiciziaInviate(Long userId) {
		return friendshipService.getSentFriendRequests(userId);

	}

	public Set<Friendship> getRichiesteAmiciziaRicevute(Long userId) {
		return friendshipService.getReceivedFriendRequests(userId);

	}

	public void rifiutaRichiestaAmicizia(Long userId, Long requesterId) {
		Friendship friendship = friendshipService.findFriendshipBetweenUsers(userId, requesterId)
				.orElseThrow(() -> new EntityNotFoundException("Richiesta non trovata"));
		if(!friendship.getStato().equals(StatoAmicizia.IN_ATTESA))
			throw new IllegalStateException("Richiesta di amicizia 'IN ATTESA' non trovata");
		friendship.setStato(StatoAmicizia.RIFIUTATA);
		friendshipService.save(friendship);
	}

	public void rimuoviAmico(Long userId, Long friendId) {
		Friendship friendship = friendshipService.findFriendshipBetweenUsers(userId, friendId)
				.orElseThrow(() -> new EntityNotFoundException("Amicizia non trovata"));

		if (!friendship.getStato().equals(StatoAmicizia.ACCETTATA))
			throw new IllegalStateException("Amicizia non trovata");

		friendshipService.delete(friendship);
	}

	public List<User> getAmici(Long userId) {
		return friendshipService.getFriendsOfUser(userId, StatoAmicizia.ACCETTATA);
	}
}
