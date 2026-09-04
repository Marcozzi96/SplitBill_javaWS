package it.javaWS.repositories;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.ShoppingItem;

@Repository
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {

	// Gli articoli da acquistare (toBuy=true) prima degli acquistati; a parità, per id crescente.
	Page<ShoppingItem> findByGroupIdOrderByToBuyDescIdAsc(Long groupId, Pageable pageable);

	Page<ShoppingItem> findByGroupIdAndToBuyOrderByToBuyDescIdAsc(Long groupId, Boolean toBuy, Pageable pageable);

	boolean existsByGroupIdAndNameIgnoreCase(Long groupId, String name);

	List<ShoppingItem> findByIdIn(Collection<Long> ids);
}
