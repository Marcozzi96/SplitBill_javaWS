package it.javaWS.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.ShoppingItem;

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

	// Gli articoli da acquistare (toBuy=true) prima degli acquistati;
	// all'interno di ogni blocco, posizione decrescente (più alta = più in alto)
	// e id crescente come tiebreaker.
	Page<ShoppingItem> findByGroupIdOrderByToBuyDescPositionDescIdAsc(Long groupId, Pageable pageable);

	Page<ShoppingItem> findByGroupIdAndToBuyOrderByToBuyDescPositionDescIdAsc(Long groupId, Boolean toBuy,
			Pageable pageable);

	// Lista completa di un gruppo, nello stesso ordine logico del servizio.
	List<ShoppingItem> findByGroupIdOrderByToBuyDescPositionDescIdAsc(Long groupId);

	// Massima posizione tra gli articoli ancora da acquistare di un gruppo.
	@Query("SELECT MAX(s.position) FROM ShoppingItem s WHERE s.group.id = :groupId AND s.toBuy = true")
	Optional<Double> findMaxPositionByGroupIdAndToBuyTrue(@Param("groupId") Long groupId);

	// Item ancora con la position di default 0: usato dal backfill una-tantum.
	@Query("SELECT s FROM ShoppingItem s WHERE s.position = 0")
	List<ShoppingItem> findByPositionZero();

	boolean existsByGroupIdAndNameIgnoreCase(Long groupId, String name);

	List<ShoppingItem> findByIdIn(Collection<Long> ids);
}
