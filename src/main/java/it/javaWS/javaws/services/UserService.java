package it.javaWS.javaws.services;

import it.javaWS.javaws.models.User;
import it.javaWS.javaws.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
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

}
