package it.javaWS.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Bill;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
	List<Bill> findByGroupId(Long groupId);

	Page<Bill> findByGroupIdOrderByDateDescIdDesc(Long groupId, Pageable pageable);

	List<Bill> findByBuyer_Id(Long userId);

	Page<Bill> findByBuyer_IdOrderByDateDescIdDesc(Long userId, Pageable pageable);

	@Query("SELECT b FROM Bill b WHERE EXISTS (SELECT 1 FROM Transaction t WHERE t.bill = b AND t.user.id = :userId) ORDER BY b.date DESC, b.id DESC")
	List<Bill> findBillsByUserIdThroughTransactions(@Param("userId") Long userId);

	@Query("SELECT b FROM Bill b WHERE EXISTS (SELECT 1 FROM Transaction t WHERE t.bill = b AND t.user.id = :userId) ORDER BY b.date DESC, b.id DESC")
	Page<Bill> findBillsByUserIdThroughTransactions(@Param("userId") Long userId, Pageable pageable);

}
