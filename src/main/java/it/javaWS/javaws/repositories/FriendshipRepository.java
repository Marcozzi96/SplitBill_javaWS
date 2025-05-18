package it.javaWS.javaws.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.javaws.models.Friendship;
import it.javaWS.javaws.models.User;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.utente1.id = :userId AND f.utente2.id = :otherId)
           OR (f.utente2.id = :userId AND f.utente1.id = :otherId)
    """)
    Optional<Friendship> findBetweenUsers(@Param("userId") Long userId, @Param("otherId") Long otherId);

    @Query("""
        SELECT CASE
            WHEN f.utente1.id = :userId THEN f.utente2
            ELSE f.utente1
        END
        FROM Friendship f
        WHERE (f.utente1.id = :userId OR f.utente2.id = :userId)
          AND f.stato = 'ACCETTATA'
    """)
    List<User> findFriendsOfUser(@Param("userId") Long userId);
}
