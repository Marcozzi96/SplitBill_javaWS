package it.javaWS.javaws.repositories;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.javaws.enums.StatoAmicizia;
import it.javaWS.javaws.models.entities.Friendship;
import it.javaWS.javaws.models.entities.User;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE (f.user1.id = :userId AND f.user2.id = :otherId)
			       OR (f.user2.id = :userId AND f.user1.id = :otherId)
			""")
	Optional<Friendship> findBetweenUsers(@Param("userId") Long userId, @Param("otherId") Long otherId);

	@Query("""
			    SELECT COUNT(f) FROM Friendship f
			    WHERE (f.user1.id = :userId AND f.user2.id IN :otherIds)
			       OR (f.user2.id = :userId AND f.user1.id IN :otherIds)
			""")
	long countFriendshipsWithUser(@Param("userId") Long userId, @Param("otherIds") Set<Long> otherIds);

	@Query("""
			    SELECT f.user2
			    FROM Friendship f
			    WHERE f.user1.id = :userId
			      AND f.stato = :statoEnum
			    UNION
			    SELECT f.user1
			    FROM Friendship f
			    WHERE f.user2.id = :userId
			      AND f.stato = :statoEnum
			""")
	List<User> findFriendsOfUser(@Param("userId") Long userId, @Param("statoEnum") StatoAmicizia statoEnum);

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE (f.userToBeConfirmed.id = :userId)
			AND f.stato = 'IN_ATTESA'
			""")
	Set<Friendship> findRequestRecByUser(@Param("userId") Long userId);

	@Query("""
			    SELECT f FROM Friendship f
			    WHERE (f.user1.id = :userId AND f.user1.id <> f.userToBeConfirmed.id)
			       OR (f.user2.id = :userId AND f.user2.id <> f.userToBeConfirmed.id)
			       AND f.stato = 'IN_ATTESA'
			""")
	Set<Friendship> findRequestSenByUser(@Param("userId") Long userId);

}
