package it.javaWS.javaws.services;

import it.javaWS.javaws.enums.StatoAmicizia;
import it.javaWS.javaws.models.entities.Friendship;
import it.javaWS.javaws.models.entities.User;
import it.javaWS.javaws.repositories.FriendshipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;

    @Autowired
    public FriendshipService(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public Optional<Friendship> findFriendshipBetweenUsers(Long userId, Long otherId) {
        return friendshipRepository.findBetweenUsers(userId, otherId);
    }

    public boolean areAllFriends(Long userId, Set<Long> otherIds) {
        long count = friendshipRepository.countFriendshipsWithUser(userId, otherIds);
        return count == otherIds.size();
    }

    public List<User> getFriendsOfUser(Long userId, StatoAmicizia stato) {
        return friendshipRepository.findFriendsOfUser(userId, stato);
    }

    public Set<Friendship> getReceivedFriendRequests(Long userId) {
        return friendshipRepository.findRequestRecByUser(userId);
    }

    public Set<Friendship> getSentFriendRequests(Long userId) {
        return friendshipRepository.findRequestSenByUser(userId);
    }
    public Friendship save(Friendship f) {
    	return friendshipRepository.save(f);
    }
    public void delete(Friendship f) {
    	friendshipRepository.delete(f);
    }
}
