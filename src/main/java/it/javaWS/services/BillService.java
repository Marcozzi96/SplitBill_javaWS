package it.javaWS.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.TransactionRepository;

@Service
public class BillService {

    private final BillRepository billRepository;//
    private final TransactionRepository transactionRepository;

    public BillService(BillRepository billRepository, TransactionRepository transactionRepository) {
        this.billRepository = billRepository;
        this.transactionRepository = transactionRepository;
    }
    
    public Bill createBill(String description, BigDecimal amount, String notes,
    		User buyer, Group group, Map<User, BigDecimal> usersDebit) {
    	
    	
        Bill bill = new Bill();
        bill.setDescription(description);
        bill.setAmount(amount);
        bill.setNotes(notes);
        bill.setDate(LocalDate.now());
        bill.setBuyer(buyer);
        bill.setGroup(group);

        Bill savedBill = billRepository.save(bill);

        // Equa divisione della spesa tra gli utenti del gruppo
        //List<User> groupUsers = new ArrayList<>(groupService.getUsersInGroup(groupId));
        
        BigDecimal a = new BigDecimal(0);
        savedBill.setTransactions(new LinkedList<Transaction>());
        
        for (User user : usersDebit.keySet()) {
            if (!user.getId().equals(buyer.getId())) {
                Transaction t = new Transaction();
                t.setUser(user);
                t.setBill(savedBill);
                t.setGroup(group);
                t.setAmount((usersDebit.get(user).multiply(new BigDecimal(-1)))); 
                a = a.add(usersDebit.get(user));
                savedBill.getTransactions().add(transactionRepository.save(t));
            }
        }
        Transaction t = new Transaction();
        
        t.setUser(buyer);
        t.setBill(savedBill);
        t.setGroup(group);
        t.setAmount(a); //devo mettere quanto ha prestato
        savedBill.getTransactions().add(transactionRepository.save(t));
        
        return savedBill;
    }

    public List<Bill> getBillsByGroup(Long groupId) {
        return billRepository.findByGroupId(groupId);
    }
    
    public List<Bill> getBillsWhereUserIsBuyer(Long userId) {
        return billRepository.findByBuyer_Id(userId);
    }
//    
//    public List<Bill> getBillsWhereUserIsDebtor(Long userId) {
//        return billRepository.findByDebtors_Id(userId);
//    }
    
    public List<Transaction> getTransactionsByBillId(Long billId) {
        return transactionRepository.findByBill_Id(billId);
    }
    
    public List<Bill> getBillsByUserId(Long userId) {
        return billRepository.findBillsByUserIdThroughTransactions(userId);
    }
    
    public void deleteBill(Long id) {
        billRepository.deleteById(id);
    }
    
    public void updateBill(Bill bill) {
    	billRepository.save(bill);
    }

}
