package it.javaWS.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.BillDTO;
import it.javaWS.models.dto.TransactionDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.BillNotFoundException;
import it.javaWS.utils.InvalidBillException;
import it.javaWS.utils.UnauthorizedAccessException;

@Service
public class BillService {

    private final BillRepository billRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceService balanceService;
    private final GroupService groupService;
    private final UserRepository userRepository;
    private final FriendshipService friendshipService;

    public BillService(BillRepository billRepository, TransactionRepository transactionRepository,
            BalanceService balanceService, GroupService groupService, UserRepository userRepository,
            FriendshipService friendshipService) {
        this.billRepository = billRepository;
        this.transactionRepository = transactionRepository;
        this.balanceService = balanceService;
        this.groupService = groupService;
        this.userRepository = userRepository;
        this.friendshipService = friendshipService;
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

        balanceService.applyBill(savedBill);

        return savedBill;
    }

    @Transactional
    public BillDTO createBillDto(String description, BigDecimal amount, String notes,
            User buyer, Group group, Map<User, BigDecimal> usersDebit) {
        return toBillDto(createBill(description, amount, notes, buyer, group, usersDebit));
    }

    @Transactional(readOnly = true)
    public List<Bill> getBillsByGroup(Long groupId) {
        return billRepository.findByGroupId(groupId);
    }

    @Transactional(readOnly = true)
    public Page<BillDTO> getBillsByGroup(Long groupId, Pageable pageable) {
        Page<Bill> bills = billRepository.findByGroupIdOrderByDateDescIdDesc(groupId, pageable);
        return bills.map(this::toBillDto);
    }

    @Transactional(readOnly = true)
    public List<Bill> getBillsWhereUserIsBuyer(Long userId) {
        return billRepository.findByBuyer_Id(userId);
    }

    @Transactional(readOnly = true)
    public Page<BillDTO> getBillsWhereUserIsBuyer(Long userId, Pageable pageable) {
        Page<Bill> bills = billRepository.findByBuyer_IdOrderByDateDescIdDesc(userId, pageable);
        return bills.map(this::toBillDto);
    }

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionsByBillId(Long billId) {
        return transactionRepository.findByBill_Id(billId);
    }

    @Transactional(readOnly = true)
    public Set<TransactionDTO> getTransactionDtosByBillId(Long billId) {
        return transactionRepository.findByBill_Id(billId).stream()
                .map(TransactionDTO::new)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<Bill> getBillsByUserId(Long userId) {
        return billRepository.findBillsByUserIdThroughTransactions(userId);
    }

    @Transactional(readOnly = true)
    public Page<BillDTO> getBillsByUserId(Long userId, Pageable pageable) {
        Page<Bill> bills = billRepository.findBillsByUserIdThroughTransactions(userId, pageable);
        List<BillDTO> dtos = bills.getContent().stream()
                .map(this::toBillDto)
                .toList();
        return new PageImpl<>(dtos, pageable, bills.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Bill getBill(Long id) {
        return billRepository.findById(id)
                .orElseThrow(() -> new BillNotFoundException("Spesa non trovata"));
    }

    @Transactional(readOnly = true)
    public BillDTO getBillDto(Long id) {
        return toBillDto(getBill(id));
    }

    @Transactional
    public void deleteBill(Long id) {
        Bill bill = getBill(id);
        balanceService.revertBill(bill);
        transactionRepository.deleteAll(transactionRepository.findByBill_Id(bill.getId()));
        billRepository.delete(bill);
    }

    @Transactional
    public void updateBill(Bill bill) {
        billRepository.save(bill);
    }

    @Transactional
    public BillDTO updateBill(Long billId, Long requestingUserId, String description,
            BigDecimal amount, String notes, Long buyerId, Map<Long, BigDecimal> usersDebit) {

        Bill bill = getBill(billId);

        Group group = bill.getGroup();
        if (group != null) {
            // Qualsiasi membro attivo del gruppo può modificare le spese del gruppo.
            if (!groupService.existsByGroupIdAndUserId(group.getId(), requestingUserId)) {
                throw new UnauthorizedAccessException("Non sei autorizzato a modificare questa spesa");
            }
        } else {
            // Spesa personale: può modificarla chiunque sia coinvolto (buyer o debitore).
            boolean involved = bill.getBuyer().getId().equals(requestingUserId)
                    || transactionRepository.findByBill_Id(billId).stream()
                            .anyMatch(t -> t.getUser().getId().equals(requestingUserId));
            if (!involved) {
                throw new UnauthorizedAccessException("Non sei autorizzato a modificare questa spesa");
            }
        }

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

        // Chi ha pagato: default il buyer attuale; buyerId consente di cambiarlo.
        User buyer = bill.getBuyer();
        if (buyerId != null && !buyerId.equals(buyer.getId())) {
            buyer = userRepository.findById(buyerId)
                    .orElseThrow(() -> new InvalidBillException("Buyer non trovato"));
        }
        final Long effectiveBuyerId = buyer.getId();

        Set<Long> debtorIds = usersDebit.keySet();
        List<User> debtors;
        if (group != null) {
            // Il buyer deve essere un membro attivo del gruppo.
            if (!groupService.existsByGroupIdAndUserId(group.getId(), effectiveBuyerId)) {
                throw new InvalidBillException("Il buyer deve far parte del gruppo");
            }
            Set<UserGroup> userGroups = groupService.getUserGroup(group.getId(), debtorIds);
            if (userGroups.size() != debtorIds.size()) {
                throw new InvalidBillException("Non tutti i debitori fanno parte del gruppo");
            }
            debtors = userGroups.stream().map(UserGroup::getUser).toList();
        } else {
            // Spesa personale: debitori esistenti e (tranne il buyer) amici del buyer.
            debtors = userRepository.findAllById(debtorIds);
            if (debtors.size() != debtorIds.size()) {
                throw new InvalidBillException("Uno o più debitori non esistono");
            }
            Set<Long> otherIds = debtorIds.stream()
                    .filter(id -> !id.equals(effectiveBuyerId))
                    .collect(Collectors.toSet());
            if (!otherIds.isEmpty() && !friendshipService.areAllFriends(effectiveBuyerId, otherIds)) {
                throw new InvalidBillException("I debitori di una spesa personale devono essere amici del buyer");
            }
        }

        // Gli utenti eliminati (soft delete) già coinvolti nella spesa restano ammessi
        // (altrimenti la spesa non sarebbe più modificabile); si bloccano solo gli
        // eliminati "nuovi": mai presenti prima, oppure scelti come nuovo buyer.
        Set<Long> partecipantiEsistenti = transactionRepository.findByBill_Id(billId).stream()
                .map(t -> t.getUser().getId())
                .collect(Collectors.toSet());
        partecipantiEsistenti.add(bill.getBuyer().getId());
        if (buyer.isDeleted() && !partecipantiEsistenti.contains(effectiveBuyerId)) {
            throw new InvalidBillException("Un utente eliminato non può partecipare a nuove spese");
        }
        for (User debtor : debtors) {
            if (debtor.isDeleted() && !partecipantiEsistenti.contains(debtor.getId())) {
                throw new InvalidBillException("Un utente eliminato non può partecipare a nuove spese");
            }
        }

        balanceService.revertBill(bill);
        transactionRepository.deleteAll(transactionRepository.findByBill_Id(bill.getId()));

        bill.setDescription(description);
        bill.setAmount(amount);
        bill.setNotes(notes);
        bill.setDate(LocalDate.now());
        bill.setBuyer(buyer);
        bill.setTransactions(new LinkedList<>());

        BigDecimal buyerCredit = BigDecimal.ZERO;

        for (User debtor : debtors) {
            BigDecimal debit = usersDebit.get(debtor.getId());
            if (!debtor.getId().equals(buyer.getId())) {
                Transaction t = new Transaction();
                t.setUser(debtor);
                t.setBill(bill);
                t.setGroup(group);
                t.setAmount(debit.negate());
                buyerCredit = buyerCredit.add(debit);
                bill.getTransactions().add(transactionRepository.save(t));
            }
        }

        Transaction buyerTransaction = new Transaction();
        buyerTransaction.setUser(buyer);
        buyerTransaction.setBill(bill);
        buyerTransaction.setGroup(group);
        buyerTransaction.setAmount(buyerCredit);
        bill.getTransactions().add(transactionRepository.save(buyerTransaction));

        Bill updatedBill = billRepository.save(bill);
        balanceService.applyBill(updatedBill);

        return toBillDto(updatedBill);
    }

    private BillDTO toBillDto(Bill bill) {
        BillDTO dto = new BillDTO(bill);
        dto.setTransactions(getTransactionDtosByBillId(bill.getId()));
        return dto;
    }
}
