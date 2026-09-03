package it.javaWS.services;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.enums.StatoAmicizia;
import it.javaWS.models.entities.Friendship;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.FriendshipRepository;

@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;

    public FriendshipService(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Friendship> findFriendshipBetweenUsers(Long userId, Long otherId) {
        return friendshipRepository.findBetweenUsers(userId, otherId);
    }

    @Transactional(readOnly = true)
    public boolean areAllFriends(Long userId, Set<Long> otherIds) {
        long count = friendshipRepository.countFriendshipsWithUser(userId, otherIds);
        return count == otherIds.size();
    }

    @Transactional(readOnly = true)
    public List<Friendship> findAllBetweenUserAndOthers(Long userId, Set<Long> otherIds) {
        return friendshipRepository.findAllBetweenUserAndOthers(userId, otherIds);
    }

    @Transactional(readOnly = true)
    public List<User> getFriendsOfUser(Long userId, StatoAmicizia stato) {
        return friendshipRepository.findFriendsOfUser(userId, stato);
    }

    @Transactional(readOnly = true)
    public Page<User> getFriendsOfUser(Long userId, StatoAmicizia stato, Pageable pageable) {
        // Già ordinati alfabeticamente dalla query.
        List<User> friends = friendshipRepository.findFriendsOfUser(userId, stato);
        return toPage(friends, pageable);
    }

    @Transactional(readOnly = true)
    public Set<Friendship> getReceivedFriendRequests(Long userId) {
        return friendshipRepository.findRequestRecByUser(userId);
    }

    @Transactional(readOnly = true)
    public Page<Friendship> getReceivedFriendRequests(Long userId, Pageable pageable) {
        return friendshipRepository.findRequestRecByUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Set<Friendship> getSentFriendRequests(Long userId) {
        return friendshipRepository.findRequestSenByUser(userId);
    }

    @Transactional(readOnly = true)
    public Page<Friendship> getSentFriendRequests(Long userId, Pageable pageable) {
        return friendshipRepository.findRequestSenByUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long countReceivedFriendRequests(Long userId) {
        return friendshipRepository.countPendingReceivedRequests(userId);
    }

    @Transactional
    public Friendship save(Friendship f) {
        return friendshipRepository.save(f);
    }

    @Transactional
    public void delete(Friendship f) {
        friendshipRepository.delete(f);
    }

    private <T> Page<T> toPage(List<T> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= list.size()) {
            return new PageImpl<>(List.of(), pageable, list.size());
        }
        int end = Math.min(start + pageable.getPageSize(), list.size());
        return new PageImpl<>(list.subList(start, end), pageable, list.size());
    }
}
