package it.javaWS.javaws.repositories;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.javaWS.javaws.models.entities.Group;
import it.javaWS.javaws.models.entities.User;
import it.javaWS.javaws.models.entities.UserGroup;
import it.javaWS.javaws.models.entities.UserGroupId;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    Set<UserGroup> findByUser(User user);
    
    Set<UserGroup> findByGroup(Group group);
    
    //@Query("DELETE FROM user_group WHERE group_id = ? AND user_id IN (?, ?, ...)")
    void deleteByGroup_IdAndUser_IdIn(Long groupId, Set<Long> userIds);
    
    Set<UserGroup> findByGroup_IdAndUser_IdIn(Long groupId, Set<Long> userIds);
    
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    
    List<UserGroup> findByGroupId(Long groupId);
    
    
}
