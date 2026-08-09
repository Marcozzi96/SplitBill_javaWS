package it.javaWS.repositories;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.models.entities.UserGroupId;
import it.javaWS.models.enums.GroupRole;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    Set<UserGroup> findByUser(User user);
    
    Set<UserGroup> findByGroup(Group group);
    
    //@Query("DELETE FROM user_group WHERE group_id = ? AND user_id IN (?, ?, ...)")
    void deleteByGroup_IdAndUser_IdIn(Long groupId, Set<Long> userIds);
    
    Set<UserGroup> findByGroup_IdAndUser_IdIn(Long groupId, Set<Long> userIds);
    
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    
    List<UserGroup> findByGroupId(Long groupId);

    @Query("SELECT COUNT(ug) > 0 FROM UserGroup ug WHERE ug.group.id = :groupId AND ug.user.id = :userId AND ug.role = :role")
    boolean existsByGroupIdAndUserIdAndRole(@Param("groupId") Long groupId, @Param("userId") Long userId, @Param("role") GroupRole role);

    @Query("SELECT ug FROM UserGroup ug WHERE ug.group.id = :groupId AND ug.dataUscita IS NULL")
    List<UserGroup> findActiveByGroupId(@Param("groupId") Long groupId);


}
