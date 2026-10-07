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

    // 3. Trova i gruppi in base all'id dell'utente con paginazione (solo membership attive).
    // Ordinamento: per ultima attività del gruppo, cioè il massimo tra la data di creazione
    // del gruppo e la data dell'ultima spesa — così un gruppo appena creato compete con i
    // gruppi che hanno spese recenti. A parità di data vince il gruppo con id più alto
    // (creato dopo). Le spese personali (group null) non contano: non appartengono a un gruppo.
    @Query("SELECT g FROM Group g JOIN g.userGroups ug WHERE ug.user.id = :userId AND ug.dataUscita IS NULL "
            + "ORDER BY GREATEST(g.creationDate, COALESCE((SELECT MAX(b.date) FROM Bill b WHERE b.group = g), g.creationDate)) DESC, "
            + "g.id DESC")
    Page<Group> getGroupsByUserId(Long userId, Pageable pageable);
}
