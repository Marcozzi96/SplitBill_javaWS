package it.javaWS.services;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.UserBalanceDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;

@Service
public class BalanceService {

    private final TransactionRepository transactionRepository;
    
    private final UserRepository userRepository;
    
    private final BillRepository billRepository;

    public BalanceService(TransactionRepository transactionRepository, UserRepository userRepository, BillRepository billRepository) {
        this.transactionRepository = transactionRepository;
		this.userRepository = userRepository;
		this.billRepository = billRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal getUserBalance(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        return transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public UserBalanceDTO getDetailedBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));

        BigDecimal totalPaid = billRepository.findByBuyer_Id(userId).stream()
                .map(Bill::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalOwed = transactionRepository.findByUserId(userId).stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new UserBalanceDTO(user.getId(), user.getUsername(), totalPaid, totalOwed);
    }

}
