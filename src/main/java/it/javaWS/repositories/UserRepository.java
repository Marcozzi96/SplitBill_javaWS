package it.javaWS.repositories;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.User;
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	
	@Query("SELECT u FROM User u WHERE u.deleted = false AND (LOWER(u.email) = LOWER(?1) OR LOWER(u.username) = LOWER(?2))")
	Set<User> findByEmailOrUsernameIgnoreCase(String email, String username);

	@Query("SELECT u FROM User u WHERE u.deleted = false AND LOWER(u.username) = LOWER(?1)")
	Optional<User> findByUsernameIgnoreCase(String username);
	
	@Query("SELECT u FROM User u WHERE u.deleted = false AND LOWER(u.email) = LOWER(?1)")
	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByUsernameAndDeletedFalse(String username);
}

