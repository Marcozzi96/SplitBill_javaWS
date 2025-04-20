package it.javaWS.javaws.services;

import it.javaWS.javaws.models.*;
import it.javaWS.javaws.repositories.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class BillService {

    private final BillRepository billRepository;//
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final TransactionRepository transactionRepository;

    public BillService(BillRepository billRepository, UserRepository userRepository,
                       GroupRepository groupRepository, TransactionRepository transactionRepository) {
        this.billRepository = billRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.transactionRepository = transactionRepository;
    }

    public Bill createBill(String description, BigDecimal amount, String notes,
                           Long buyerId, Long groupId) {

        User buyer = userRepository.findById(buyerId).orElseThrow();
        Group group = groupRepository.findById(groupId).orElseThrow();

        Bill bill = new Bill();
        bill.setDescription(description);
        bill.setAmount(amount);
        bill.setNotes(notes);
        bill.setDate(LocalDate.now());
        bill.setBuyer(buyer);
        bill.setGroup(group);

        Bill savedBill = billRepository.save(bill);

        // Equa divisione della spesa tra gli utenti del gruppo
        List<User> groupUsers = new ArrayList<>(group.getUsers());
        BigDecimal splitAmount = amount.divide(BigDecimal.valueOf(groupUsers.size()), RoundingMode.HALF_UP);
        
        for (User user : groupUsers) {
            if (!user.getId().equals(buyer.getId())) {
                Transaction t = new Transaction();
                t.setUser(user);
                t.setBill(savedBill);
                t.setGroup(group);
                t.setAmount(splitAmount);
                transactionRepository.save(t);
            }
        }

        return savedBill;
    }

    public List<Bill> getBillsByGroup(Long groupId) {
        return billRepository.findByGroupId(groupId);
    }
    
    public void deleteBill(Long id) {
        billRepository.deleteById(id);
    }
    
    public void updateBill(Bill bill) {
    	billRepository.save(bill);
    }

}
