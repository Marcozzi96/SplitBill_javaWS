package it.javaWS.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    // 1. Trova i gruppi in base all'utente (solo membership attive: dataUscita null)
    @Query("SELECT g FROM Group g JOIN g.userGroups ug WHERE ug.user = :user AND ug.dataUscita IS NULL")
    List<Group> getGroupsByUser(User user);

    // 2. Trova i gruppi in base all'id dell'utente (solo membership attive)
    @Query("SELECT g FROM Group g JOIN g.userGroups ug WHERE ug.user.id = :userId AND ug.dataUscita IS NULL")
    List<Group> getGroupsByUserId(Long userId);

    // 3. Trova i gruppi in base all'id dell'utente con paginazione (solo membership attive)
    @Query("SELECT g FROM Group g JOIN g.userGroups ug WHERE ug.user.id = :userId AND ug.dataUscita IS NULL")
    Page<Group> getGroupsByUserId(Long userId, Pageable pageable);
}
