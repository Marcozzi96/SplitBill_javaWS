package it.javaWS.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.javaWS.enums.AuthTokenType;
import it.javaWS.models.entities.AuthToken;
import it.javaWS.models.entities.User;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    @Query("SELECT t FROM AuthToken t LEFT JOIN FETCH t.user WHERE t.token = ?1")
    Optional<AuthToken> findByToken(String token);

    List<AuthToken> findByUserAndTypeAndUsedFalse(User user, AuthTokenType type);
}
