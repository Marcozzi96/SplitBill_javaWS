package it.javaWS.services;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.FriendshipRepository;

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
    	Set<Friendship> f = friendshipRepository.findRequestSenByUser(userId);
        return f;
    }
    public Friendship save(Friendship f) {
    	return friendshipRepository.save(f);
    }
    public void delete(Friendship f) {
    	friendshipRepository.delete(f);
    }
}
