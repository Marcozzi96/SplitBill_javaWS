package it.javaWS.javaws.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import it.javaWS.javaws.models.Bill;

import java.util.List;

public interface BillRepository extends JpaRepository<Bill, Long> {
    List<Bill> findByGroupId(Long groupId);
    
    List<Bill> findByDebtors_Id(Long userId);

    List<Bill> findByBuyer_Id(Long userId);
    
    
}