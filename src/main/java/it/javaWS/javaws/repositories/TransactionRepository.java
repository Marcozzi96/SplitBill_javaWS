package it.javaWS.javaws.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import it.javaWS.javaws.models.Transaction;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);
    
    List<Transaction> findByBill_Id(Long billId);
    
}