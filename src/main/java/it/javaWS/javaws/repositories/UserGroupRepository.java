package it.javaWS.javaws.repositories;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.models.UserGroup;
import it.javaWS.javaws.models.UserGroupId;

@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    Set<UserGroup> findByUser(User user);
    Set<UserGroup> findByGroup(Group group);
    
}
