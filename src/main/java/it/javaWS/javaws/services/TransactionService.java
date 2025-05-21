package it.javaWS.javaws.services;

import org.springframework.stereotype.Service;
import it.javaWS.javaws.models.Transaction;
import it.javaWS.javaws.repositories.TransactionRepository;

@Service
public class TransactionService {
	
	private final TransactionRepository transactionRepository;
	
	public TransactionService(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
		
	}
	
	public void deleteTransaction(Long id) {
	    transactionRepository.deleteById(id);
	}
	
	public void updateTransaction(Transaction transaction) {
		transactionRepository.save(transaction);
    }

}
