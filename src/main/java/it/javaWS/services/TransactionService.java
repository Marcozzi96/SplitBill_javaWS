package it.javaWS.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.TransactionDTO;
import it.javaWS.models.entities.Transaction;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.utils.TransactionNotFoundException;

@Service
public class TransactionService {
	
	private final TransactionRepository transactionRepository;
	
	public TransactionService(TransactionRepository transactionRepository) {
		this.transactionRepository = transactionRepository;
		
	}

	@Transactional(readOnly = true)
	public Transaction getTransaction(Long id) {
		return transactionRepository.findById(id)
				.orElseThrow(() -> new TransactionNotFoundException("Transazione non trovata"));
	}

	@Transactional(readOnly = true)
	public TransactionDTO getTransactionDto(Long id) {
		return new TransactionDTO(getTransaction(id));
	}
	
	@Transactional
	public void deleteTransaction(Long id) {
	    transactionRepository.deleteById(id);
	}

	@Transactional
	public void updateTransaction(Transaction transaction) {
		transactionRepository.save(transaction);
    }

}
