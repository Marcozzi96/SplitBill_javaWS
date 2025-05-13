package it.javaWS.javaws.services;

import it.javaWS.javaws.models.*;
import it.javaWS.javaws.repositories.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BillService {

    private final BillRepository billRepository;//
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final TransactionRepository transactionRepository;
    private final GroupService groupService;

    public BillService(BillRepository billRepository, UserRepository userRepository,
                       GroupRepository groupRepository, TransactionRepository transactionRepository, GroupService groupService, UserGroupRepository userGroupRepository) {
        this.billRepository = billRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
		this.userGroupRepository = userGroupRepository;
        this.transactionRepository = transactionRepository;
		this.groupService = groupService;
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
    
    public void deleteBill(Long id) {
        billRepository.deleteById(id);
    }
    
    public void updateBill(Bill bill) {
    	billRepository.save(bill);
    }

}
