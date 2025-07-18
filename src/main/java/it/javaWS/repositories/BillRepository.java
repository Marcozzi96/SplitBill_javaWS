package it.javaWS.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.models.entities.Bill;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
	List<Bill> findByGroupId(Long groupId);

	List<Bill> findByBuyer_Id(Long userId);

	@Query("SELECT DISTINCT t.bill FROM Transaction t WHERE t.user.id = :userId")
	List<Bill> findBillsByUserIdThroughTransactions(@Param("userId") Long userId);

}