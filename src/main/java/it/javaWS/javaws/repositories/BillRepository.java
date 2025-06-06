package it.javaWS.javaws.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import it.javaWS.javaws.models.Bill;

import java.util.List;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
	List<Bill> findByGroupId(Long groupId);

	List<Bill> findByBuyer_Id(Long userId);

	@Query("SELECT DISTINCT t.bill FROM Transaction t WHERE t.user.id = :userId")
	List<Bill> findBillsByUserIdThroughTransactions(@Param("userId") Long userId);

}