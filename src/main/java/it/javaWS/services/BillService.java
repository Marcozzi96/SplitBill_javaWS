package it.javaWS.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.utils.InvalidBillException;

@Service
public class BillService {

    private final BillRepository billRepository;//
    private final TransactionRepository transactionRepository;

    public BillService(BillRepository billRepository, TransactionRepository transactionRepository) {
        this.billRepository = billRepository;
        this.transactionRepository = transactionRepository;
    }
    
    @Transactional
    public Bill createBill(String description, BigDecimal amount, String notes,
            User buyer, Group group, Map<User, BigDecimal> usersDebit) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidBillException("L'importo della spesa deve essere positivo");
        }

        if (usersDebit == null || usersDebit.isEmpty()) {
            throw new InvalidBillException("La mappa dei debiti non può essere vuota");
        }

        BigDecimal totalDebit = usersDebit.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(totalDebit) != 0) {
            throw new InvalidBillException("La somma dei debiti (" + totalDebit
                    + ") non corrisponde all'importo totale (" + amount + ")");
        }

        Bill bill = new Bill();
        bill.setDescription(description);
        bill.setAmount(amount);
        bill.setNotes(notes);
        bill.setDate(LocalDate.now());
        bill.setBuyer(buyer);
        bill.setGroup(group);

        Bill savedBill = billRepository.save(bill);
        savedBill.setTransactions(new LinkedList<>());

        // Il credito del buyer è uguale alla somma dei debiti degli altri partecipanti.
        // Se il buyer è anche debitore, il proprio debito riduce il credito in modo implicito.
        BigDecimal buyerCredit = BigDecimal.ZERO;
        for (User user : usersDebit.keySet()) {
            if (!user.getId().equals(buyer.getId())) {
                Transaction t = new Transaction();
                t.setUser(user);
                t.setBill(savedBill);
                t.setGroup(group);
                BigDecimal debit = usersDebit.get(user);
                t.setAmount(debit.negate());
                buyerCredit = buyerCredit.add(debit);
                savedBill.getTransactions().add(transactionRepository.save(t));
            }
        }

        Transaction buyerTransaction = new Transaction();
        buyerTransaction.setUser(buyer);
        buyerTransaction.setBill(savedBill);
        buyerTransaction.setGroup(group);
        buyerTransaction.setAmount(buyerCredit);
        savedBill.getTransactions().add(transactionRepository.save(buyerTransaction));

        return savedBill;
    }

    @Transactional(readOnly = true)
    public List<Bill> getBillsByGroup(Long groupId) {
        return billRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public List<Bill> getBillsWhereUserIsBuyer(Long userId) {
        return billRepository.findByBuyer_Id(userId);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByBillId(Long billId) {
        return transactionRepository.findByBill_Id(billId);
    }

    @Transactional(readOnly = true)
    public List<Bill> getBillsByUserId(Long userId) {
        return billRepository.findBillsByUserIdThroughTransactions(userId);
    }

    @Transactional(readOnly = true)
    public Bill getBill(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Spesa non trovata"));
    }

    @Transactional
    public void deleteBill(Long id) {
        billRepository.deleteById(id);
    }

    @Transactional
    public void updateBill(Bill bill) {
    	billRepository.save(bill);
    }

}
