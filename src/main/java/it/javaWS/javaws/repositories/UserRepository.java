package it.javaWS.javaws.repositories;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import it.javaWS.javaws.models.User;

public interface UserRepository extends JpaRepository<User, Long> {
	
	@Query("select u from User u where u.email = ?1 or u.username = ?2")
	Set<User> findByEmailOrUsername(String emailAddress, String username);

	Optional<User> findByUsername(String username);
	
	boolean existsByUsername(String username);
}

