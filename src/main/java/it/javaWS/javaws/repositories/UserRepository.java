package it.javaWS.javaws.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import it.javaWS.javaws.models.User;

public interface UserRepository extends JpaRepository<User, Long> {
}