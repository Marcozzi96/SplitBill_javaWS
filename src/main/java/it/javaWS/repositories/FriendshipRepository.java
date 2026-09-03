package it.javaWS.repositories;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.User;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE f.user1.deleted = false AND f.user2.deleted = false
			      AND ((f.user1.id = :userId AND f.user2.id = :otherId)
			       OR (f.user2.id = :userId AND f.user1.id = :otherId))
			""")
	Optional<Friendship> findBetweenUsers(@Param("userId") Long userId, @Param("otherId") Long otherId);

	@Query("""
			    SELECT COUNT(f) FROM Friendship f
			    WHERE f.user1.deleted = false AND f.user2.deleted = false
			      AND ((f.user1.id = :userId AND f.user2.id IN :otherIds)
			       OR (f.user2.id = :userId AND f.user1.id IN :otherIds))
			""")
	long countFriendshipsWithUser(@Param("userId") Long userId, @Param("otherIds") Set<Long> otherIds);

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE f.user1.deleted = false AND f.user2.deleted = false
			      AND ((f.user1.id = :userId AND f.user2.id IN :otherIds)
			       OR (f.user2.id = :userId AND f.user1.id IN :otherIds))
			""")
	List<Friendship> findAllBetweenUserAndOthers(@Param("userId") Long userId, @Param("otherIds") Set<Long> otherIds);

	// La coppia (user1, user2) è ordinata e univoca: l'EXISTS seleziona "l'altro"
	// utente dell'amicizia; l'ordinamento alfabetico (case-insensitive) lo fa il DB.
	@Query("""
			    SELECT u FROM User u
			    WHERE u.deleted = false
			      AND EXISTS (
			        SELECT 1 FROM Friendship f
			        WHERE f.stato = :statoEnum
			          AND ((f.user1.id = :userId AND f.user2 = u)
			            OR (f.user2.id = :userId AND f.user1 = u))
			      )
			    ORDER BY LOWER(u.username)
			""")
	List<User> findFriendsOfUser(@Param("userId") Long userId, @Param("statoEnum") StatoAmicizia statoEnum);

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE f.userToBeConfirmed.id = :userId
			      AND f.user1.deleted = false
			      AND f.user2.deleted = false
			      AND f.stato = 'IN_ATTESA'
			""")
	Set<Friendship> findRequestRecByUser(@Param("userId") Long userId);

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE f.userToBeConfirmed.id = :userId
			      AND f.user1.deleted = false
			      AND f.user2.deleted = false
			      AND f.stato = 'IN_ATTESA'
			""")
	Page<Friendship> findRequestRecByUser(@Param("userId") Long userId, Pageable pageable);

	@Query("""
		    SELECT f FROM Friendship f
		    WHERE (
		        (f.user1.id = :userId AND f.user1.id <> f.userToBeConfirmed.id)
		        OR (f.user2.id = :userId AND f.user2.id <> f.userToBeConfirmed.id)
		    )
		    AND f.user1.deleted = false
		    AND f.user2.deleted = false
		    AND f.stato = 'IN_ATTESA'
		""")
	Set<Friendship> findRequestSenByUser(@Param("userId") Long userId);

	@Query("""
		    SELECT f FROM Friendship f
		    WHERE (
		        (f.user1.id = :userId AND f.user1.id <> f.userToBeConfirmed.id)
		        OR (f.user2.id = :userId AND f.user2.id <> f.userToBeConfirmed.id)
		    )
		    AND f.user1.deleted = false
		    AND f.user2.deleted = false
		    AND f.stato = 'IN_ATTESA'
		""")
	Page<Friendship> findRequestSenByUser(@Param("userId") Long userId, Pageable pageable);

	@Query("""
			    SELECT COUNT(f) FROM Friendship f
			    WHERE f.userToBeConfirmed.id = :userId
			      AND f.user1.deleted = false
			      AND f.user2.deleted = false
			      AND f.stato = 'IN_ATTESA'
			""")
	long countPendingReceivedRequests(@Param("userId") Long userId);

}
