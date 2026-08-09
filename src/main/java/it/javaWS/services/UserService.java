package it.javaWS.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.dto.UpdateUserRequest;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.DuplicateUserException;
import it.javaWS.utils.InvalidCredentialsException;
import jakarta.persistence.EntityNotFoundException;

@Service
public class UserService implements UserDetailsService {

	private final UserRepository userRepository;
	private final FriendshipService friendshipService;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, FriendshipService friendshipService, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.friendshipService = friendshipService;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User createUser(User user) {

		if (!userRepository.findByEmailOrUsernameIgnoreCase(user.getEmail(), user.getUsername()).isEmpty())
			return null; // Username o Email già utilizzati
		return userRepository.save(user);
	}

	@Transactional(readOnly = true)
	public Optional<User> getUser(Long id) {
		return userRepository.findById(id);
	}

	@Transactional(readOnly = true)
	public List<User> getAllUsers() {
		return userRepository.findAll();
	}

	@Transactional
	public Boolean deleteUser(Long id) {
		if (getUser(id).isEmpty())
			return false;
		userRepository.deleteById(id);
		return true;
	}

	@Transactional
	public User updateUser(User user) {
		return userRepository.save(user);
	}

	@Transactional
	public User updateUser(User user, UpdateUserRequest request) {
		if (request.getOldPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
			throw new InvalidCredentialsException("Password non valida");
		}

		if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
			if (!userRepository.findByEmailOrUsernameIgnoreCase(request.getEmail(), request.getEmail()).isEmpty()) {
				throw new DuplicateUserException("Email già in uso");
			}
			user.setEmail(request.getEmail().toLowerCase());
		}

		if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
			if (!userRepository.findByEmailOrUsernameIgnoreCase(request.getUsername(), request.getUsername()).isEmpty()) {
				throw new DuplicateUserException("Username già in uso");
			}
			user.setUsername(request.getUsername());
		}

		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			user.setPassword(passwordEncoder.encode(request.getPassword()));
		}

		return userRepository.save(user);
	}

	@Override
	@Transactional(readOnly = true)
	public User loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findByUsernameIgnoreCase(username)
				.orElseThrow(() -> new UsernameNotFoundException("Credenziali non valide"));
	}

	@Transactional(readOnly = true)
	public User loadUserByEmailOrUsername(String email, String username) {
		Set<User> users = userRepository.findByEmailOrUsernameIgnoreCase(email, username);
		if (users.size() != 1) {
			throw new BadCredentialsException("Credenziali non valide");
		}

		return users.stream().findFirst().get();
	}

	@Transactional(readOnly = true)
	public User getByUsername(String username) {
		return userRepository.findByUsernameIgnoreCase(username).orElse(null);
	}

	@Transactional(readOnly = true)
	public Boolean existsByUsername(String username) {
		return userRepository.existsByUsernameAndDeletedFalse(username);
	}

	@Transactional(readOnly = true)
	public Boolean existsByUsernameOrEmail(User user) {
		return !userRepository.findByEmailOrUsernameIgnoreCase(user.getEmail(), user.getUsername()).isEmpty(); // Username o Email già utilizzati
	}

	@Transactional
	public void inviaRichiestaAmicizia(Long userId, Long otherId, String message) throws Exception {
		if (userId.equals(otherId))
			throw new IllegalArgumentException("Non puoi aggiungere te stesso");

		// Ordinamento per garantire utente1 < utente2
		Long user1Id = Math.min(userId, otherId);
		Long user2Id = Math.max(userId, otherId);

		Optional<Friendship> existing = friendshipService.findFriendshipBetweenUsers(user1Id, user2Id);
		if (existing.isPresent()) { // Esiste la riga
			if (existing.get().getUserToBeConfirmed().getId().equals(userId)) { // l'utente che deve confermare è lo stesso
																			// che prova a fare richiesta
				if (existing.get().getStato().equals(StatoAmicizia.IN_ATTESA)) {
					throw new IllegalStateException("Amicizia già in attesa di conferma");
				} else if (existing.get().getStato().equals(StatoAmicizia.ACCETTATA)) {
					throw new IllegalStateException("Siete già amici");
				} else if (existing.get().getStato().equals(StatoAmicizia.RIFIUTATA)) { // Devo aggiornare la riga
																						// esistente
					Friendship f = existing.get();
					User userToBeConfirmed = userRepository.findById(otherId)
							.orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));
					f.setUserToBeConfirmed(userToBeConfirmed); // cambiare l'utente che deve accettare
					f.setStato(StatoAmicizia.IN_ATTESA); // Rimettere la richiesta in attesa
					f.setMessaggio(message);
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
		friendship.setMessaggio(message);

		friendshipService.save(friendship);
	}

	@Transactional
	public void accettaRichiestaAmicizia(Long userId, Long requesterId) {
		Friendship friendship = friendshipService.findFriendshipBetweenUsers(userId, requesterId)
				.orElseThrow(() -> new EntityNotFoundException("Richiesta non trovata"));
		if(!friendship.getUserToBeConfirmed().getId().equals(userId)) {
			throw new IllegalStateException("La richiesta non può essere accettata da chi la invia");
		}
		if (friendship.getStato() != StatoAmicizia.IN_ATTESA) {
			throw new IllegalStateException("La richiesta non è in attesa");
		}

		friendship.setStato(StatoAmicizia.ACCETTATA);
		friendshipService.save(friendship);
	}

	@Transactional(readOnly = true)
	public Set<Friendship> getRichiesteAmiciziaInviate(Long userId) {
		return friendshipService.getSentFriendRequests(userId);

	}

	@Transactional(readOnly = true)
	public Set<Friendship> getRichiesteAmiciziaRicevute(Long userId) {
		return friendshipService.getReceivedFriendRequests(userId);

	}

	@Transactional
	public void rifiutaRichiestaAmicizia(Long userId, Long requesterId) {
		Friendship friendship = friendshipService.findFriendshipBetweenUsers(userId, requesterId)
				.orElseThrow(() -> new EntityNotFoundException("Richiesta non trovata"));
		if(!friendship.getStato().equals(StatoAmicizia.IN_ATTESA))
			throw new IllegalStateException("Richiesta di amicizia 'IN ATTESA' non trovata");
		if(!friendship.getUserToBeConfirmed().getId().equals(userId)) {
			friendshipService.delete(friendship); //Se vuoi annullare una tua richiesta, questa viene eliminata
		}else {
			friendship.setStato(StatoAmicizia.RIFIUTATA); //se vuoi annullare la richiesta fatta a te, questa viene messa in stato rifiutato
			friendshipService.save(friendship); //per impedire che possa esserti mandata di nuovo dalla stessa persona
			
		}
		
	}

	@Transactional
	public void rimuoviAmico(Long userId, Long friendId) {
		Friendship friendship = friendshipService.findFriendshipBetweenUsers(userId, friendId)
				.orElseThrow(() -> new EntityNotFoundException("Amicizia non trovata"));

		if (!friendship.getStato().equals(StatoAmicizia.ACCETTATA))
			throw new IllegalStateException("Amicizia non trovata");

		friendshipService.delete(friendship);
	}

	@Transactional(readOnly = true)
	public List<User> getAmici(Long userId) {
		return friendshipService.getFriendsOfUser(userId, StatoAmicizia.ACCETTATA);
	}
}
