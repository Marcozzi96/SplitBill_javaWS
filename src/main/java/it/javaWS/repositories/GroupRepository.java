package it.javaWS.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    // 1. Trova i gruppi in base all'utente
    @Query("SELECT g FROM Group g JOIN g.userGroups ug WHERE ug.user = :user")
    List<Group> getGroupsByUser(User user);

    // 2. Trova i gruppi in base all'id dell'utente
    @Query("SELECT g FROM Group g JOIN g.userGroups ug WHERE ug.user.id = :userId")
    List<Group> getGroupsByUserId(Long userId);
}