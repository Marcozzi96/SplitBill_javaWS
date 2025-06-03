package it.javaWS.javaws.repositories;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.javaWS.javaws.models.User;
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	
	@Query("SELECT u FROM User u WHERE LOWER(u.email) = LOWER(?1) OR LOWER(u.username) = LOWER(?2)")
	Set<User> findByEmailOrUsernameIgnoreCase(String email, String username);

	Optional<User> findByUsernameIgnoreCase(String username);
	
	boolean existsByUsername(String username);
}

