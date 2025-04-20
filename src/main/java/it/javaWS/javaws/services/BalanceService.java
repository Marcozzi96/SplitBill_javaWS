package it.javaWS.javaws.services;

import it.javaWS.javaws.dto.UserBalanceDTO;
import it.javaWS.javaws.models.Bill;
import it.javaWS.javaws.models.Transaction;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.repositories.BillRepository;
import it.javaWS.javaws.repositories.TransactionRepository;
import it.javaWS.javaws.repositories.UserRepository;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

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

    public BigDecimal getUserBalance(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        return transactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    public UserBalanceDTO getDetailedBalance(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();

        // Somma di quanto ha pagato come buyer
        BigDecimal totalPaid = billRepository
            .findById(userId)
            .stream()
            .map(Bill::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Somma di quanto deve in tutte le transazioni
        BigDecimal totalOwed = transactionRepository
            .findByUserId(userId)
            .stream()
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new UserBalanceDTO(user.getId(), user.getUsername(), totalPaid, totalOwed);
    }

}
