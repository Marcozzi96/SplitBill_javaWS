package it.javaWS.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.SettlementDTO;
import it.javaWS.models.dto.UserBalanceDTO;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.dto.UserSettlementDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.PairwiseSettlement;
import it.javaWS.models.entities.Payment;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserBalance;
import it.javaWS.models.entities.UserGroupBalance;
import it.javaWS.models.enums.SettlementDirection;
import it.javaWS.repositories.BillRepository;
import it.javaWS.repositories.PairwiseSettlementRepository;
import it.javaWS.repositories.PaymentRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserBalanceRepository;
import it.javaWS.repositories.UserGroupBalanceRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.InvalidPaymentException;
import it.javaWS.utils.PaymentExceedsDebtException;
import jakarta.persistence.EntityNotFoundException;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    private final TransactionRepository transactionRepository;
    private final BillRepository billRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final UserGroupBalanceRepository userGroupBalanceRepository;
    private final PairwiseSettlementRepository pairwiseSettlementRepository;

    public BalanceService(TransactionRepository transactionRepository, BillRepository billRepository,
            PaymentRepository paymentRepository, UserRepository userRepository,
            UserBalanceRepository userBalanceRepository,
            UserGroupBalanceRepository userGroupBalanceRepository,
            PairwiseSettlementRepository pairwiseSettlementRepository) {
        this.transactionRepository = transactionRepository;
        this.billRepository = billRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.userGroupBalanceRepository = userGroupBalanceRepository;
        this.pairwiseSettlementRepository = pairwiseSettlementRepository;
    }

    // Nota: saldi e settlement sono mantenuti in modo incrementale dalle singole
    // operazioni (applyBill/revertBill/applyPayment/transferUserSettlementsToGlobal).
    // Non esiste più una ricostruzione all'avvio: cancellava rimborsi e trasferimenti
    // a ogni restart in produzione.

    @Transactional(readOnly = true)
    public BigDecimal getUserBalance(Long userId) {
        return userBalanceRepository.findByUserId(userId)
                .map(UserBalance::getNetBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public UserBalanceDTO getDetailedBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));

        UserBalance balance = userBalanceRepository.findByUserId(userId).orElse(null);
        if (balance == null) {
            return new UserBalanceDTO(user.getId(), user.getUsername(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        return new UserBalanceDTO(user.getId(), user.getUsername(), balance.getTotalPaid(), balance.getTotalOwed());
    }

    @Transactional(readOnly = true)
    public UserBalanceDTO getDetailedGroupBalance(Long userId, Long groupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));

        UserGroupBalance balance = userGroupBalanceRepository.findByUserIdAndGroupId(userId, groupId).orElse(null);
        if (balance == null) {
            return new UserBalanceDTO(user.getId(), user.getUsername(), BigDecimal.ZERO, BigDecimal.ZERO);
        }

        return new UserBalanceDTO(user.getId(), user.getUsername(), balance.getTotalPaid(), balance.getTotalOwed());
    }

    @Transactional(readOnly = true)
    public List<UserSettlementDTO> getUserSettlements(Long userId) {
        List<PairwiseSettlement> settlements = pairwiseSettlementRepository.findByDebtorIdOrCreditorId(userId, userId);
        // Aggrega per coppia (controparte, gruppo): groupId null = debito personale fuori dai gruppi.
        Map<String, BigDecimal> aggregated = new HashMap<>();
        Map<String, PairwiseSettlement> referenceByKey = new HashMap<>();

        for (PairwiseSettlement settlement : settlements) {
            String key = scopeKey(settlement, userId);
            aggregated.merge(key, signedAmount(settlement, userId), BigDecimal::add);
            referenceByKey.putIfAbsent(key, settlement);
        }

        List<UserSettlementDTO> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : aggregated.entrySet()) {
            PairwiseSettlement reference = referenceByKey.get(entry.getKey());
            Long counterpartyId = counterpartyId(reference, userId);
            UserSettlementDTO dto = toUserSettlementDto(counterpartyId, entry.getValue(), reference.getGroup());
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<UserSettlementDTO> getUserGroupSettlements(Long userId, Long groupId) {
        List<PairwiseSettlement> settlements = new ArrayList<>();
        settlements.addAll(pairwiseSettlementRepository.findByDebtorIdAndGroupId(userId, groupId));
        settlements.addAll(pairwiseSettlementRepository.findByCreditorIdAndGroupId(userId, groupId));

        Map<Long, BigDecimal> aggregated = new HashMap<>();
        Group group = null;
        for (PairwiseSettlement settlement : settlements) {
            group = settlement.getGroup();
            aggregated.merge(counterpartyId(settlement, userId), signedAmount(settlement, userId), BigDecimal::add);
        }

        List<UserSettlementDTO> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : aggregated.entrySet()) {
            UserSettlementDTO dto = toUserSettlementDto(entry.getKey(), entry.getValue(), group);
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    // Debiti pendenti netti: i cicli a saldo zero non bloccano l'eliminazione.
    @Transactional(readOnly = true)
    public List<SettlementDTO> getGroupSettlementStatus(Long groupId) {
        return getSimplifiedGroupSettlements(groupId);
    }

    @Transactional(readOnly = true)
    public List<SettlementDTO> getSimplifiedGroupSettlements(Long groupId) {
        return simplifyGroupSettlements(pairwiseSettlementRepository.findByGroupId(groupId));
    }

    private List<SettlementDTO> simplifyGroupSettlements(List<PairwiseSettlement> settlements) {
        Map<User, BigDecimal> netBalances = calculateGroupNetBalances(settlements);
        List<User> debtors = netBalances.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) < 0)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(User::getId))
                .toList();
        List<User> creditors = netBalances.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(BigDecimal.ZERO) > 0)
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(User::getId))
                .toList();

        List<SettlementDTO> result = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            User debtor = debtors.get(debtorIndex);
            User creditor = creditors.get(creditorIndex);
            BigDecimal debt = netBalances.get(debtor).negate();
            BigDecimal credit = netBalances.get(creditor);
            BigDecimal amount = debt.min(credit);
            result.add(new SettlementDTO(new UserDTO(debtor), new UserDTO(creditor), amount));

            netBalances.put(debtor, debt.subtract(amount).negate());
            netBalances.put(creditor, credit.subtract(amount));
            if (debt.compareTo(amount) == 0) {
                debtorIndex++;
            }
            if (credit.compareTo(amount) == 0) {
                creditorIndex++;
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal getGroupPaymentLimit(Long payerId, Long payeeId, Long groupId) {
        return calculateGroupPaymentLimit(payerId, payeeId,
                calculateGroupNetBalances(pairwiseSettlementRepository.findByGroupId(groupId)));
    }

    /**
     * Trasferisce a livello personale soltanto i trasferimenti semplificati che
     * coinvolgono l'uscente. Ogni debito personale è rappresentato da una spesa
     * automatica e il corrispondente debito di gruppo da un rimborso automatico.
     */
    @Transactional
    public void transferUserSettlementsToGlobal(Group group, Long userId) {
        List<PairwiseSettlement> settlements = pairwiseSettlementRepository.findByGroupId(group.getId());
        Map<Long, User> usersById = new HashMap<>();
        for (PairwiseSettlement settlement : settlements) {
            usersById.put(settlement.getDebtor().getId(), settlement.getDebtor());
            usersById.put(settlement.getCreditor().getId(), settlement.getCreditor());
        }

        for (SettlementDTO settlement : simplifyGroupSettlements(settlements)) {
            Long debtorId = settlement.getDebtor().getUserId();
            Long creditorId = settlement.getCreditor().getUserId();
            if (!debtorId.equals(userId) && !creditorId.equals(userId)) {
                continue;
            }
            User debtor = usersById.get(debtorId);
            User creditor = usersById.get(creditorId);
            BigDecimal amount = settlement.getAmount();
            createPersonalTransferBill(group, debtor, creditor, amount);
            createGroupTransferPayment(group, debtor, creditor, amount);
        }
    }

    private void createPersonalTransferBill(Group group, User debtor, User creditor, BigDecimal amount) {
        Bill bill = new Bill();
        bill.setDescription("Trasferimento da " + group.getName());
        bill.setNotes(debtor.getUsername() + " deve " + amount.stripTrailingZeros().toPlainString()
                + " a " + creditor.getUsername());
        bill.setAmount(amount);
        bill.setDate(LocalDate.now());
        bill.setBuyer(creditor);
        billRepository.save(bill);

        Transaction buyerTransaction = new Transaction();
        buyerTransaction.setUser(creditor);
        buyerTransaction.setBill(bill);
        buyerTransaction.setAmount(amount);
        transactionRepository.save(buyerTransaction);

        Transaction debtorTransaction = new Transaction();
        debtorTransaction.setUser(debtor);
        debtorTransaction.setBill(bill);
        debtorTransaction.setAmount(amount.negate());
        transactionRepository.save(debtorTransaction);
        applyBill(bill);
    }

    private void createGroupTransferPayment(Group group, User debtor, User creditor, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setPayer(debtor);
        payment.setPayee(creditor);
        payment.setGroup(group);
        payment.setAmount(amount);
        payment.setDate(LocalDate.now());
        payment.setNotes("trasferimento su conto personale");
        applyPayment(paymentRepository.save(payment));
    }

    @Transactional
    public void applyBill(Bill bill) {
        // group null = spesa personale: aggiorna solo saldi globali e settlement personali.
        if (bill == null || bill.getBuyer() == null) {
            return;
        }

        User buyer = bill.getBuyer();
        Group group = bill.getGroup();
        BigDecimal totalAmount = bill.getAmount();

        BigDecimal buyerCredit = BigDecimal.ZERO;
        Map<User, BigDecimal> debtorAmounts = new HashMap<>();

        List<Transaction> transactions = transactionRepository.findByBill_Id(bill.getId());
        for (Transaction transaction : transactions) {
            User user = transaction.getUser();
            BigDecimal amount = transaction.getAmount();
            if (user.getId().equals(buyer.getId())) {
                buyerCredit = amount;
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                debtorAmounts.merge(user, amount.negate(), BigDecimal::add);
            }
        }

        BigDecimal buyerDebt = totalAmount.subtract(buyerCredit);
        if (buyerDebt.compareTo(BigDecimal.ZERO) < 0) {
            buyerDebt = BigDecimal.ZERO;
        }

        updateUserBalance(buyer, buyer, group, totalAmount, buyerDebt);
        for (Map.Entry<User, BigDecimal> entry : debtorAmounts.entrySet()) {
            User debtor = entry.getKey();
            BigDecimal amount = entry.getValue();
            updateUserBalance(debtor, buyer, group, BigDecimal.ZERO, amount);
            addPairwiseDebt(debtor, buyer, group, amount);
        }
    }

    @Transactional
    public void revertBill(Bill bill) {
        // group null = spesa personale: aggiorna solo saldi globali e settlement personali.
        if (bill == null || bill.getBuyer() == null) {
            return;
        }

        User buyer = bill.getBuyer();
        Group group = bill.getGroup();
        BigDecimal totalAmount = bill.getAmount();

        BigDecimal buyerCredit = BigDecimal.ZERO;
        Map<User, BigDecimal> debtorAmounts = new HashMap<>();

        List<Transaction> transactions = transactionRepository.findByBill_Id(bill.getId());
        for (Transaction transaction : transactions) {
            User user = transaction.getUser();
            BigDecimal amount = transaction.getAmount();
            if (user.getId().equals(buyer.getId())) {
                buyerCredit = amount;
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                debtorAmounts.merge(user, amount.negate(), BigDecimal::add);
            }
        }

        BigDecimal buyerDebt = totalAmount.subtract(buyerCredit);
        if (buyerDebt.compareTo(BigDecimal.ZERO) < 0) {
            buyerDebt = BigDecimal.ZERO;
        }

        updateUserBalance(buyer, buyer, group, totalAmount.negate(), buyerDebt.negate());
        for (Map.Entry<User, BigDecimal> entry : debtorAmounts.entrySet()) {
            User debtor = entry.getKey();
            BigDecimal amount = entry.getValue();
            updateUserBalance(debtor, buyer, group, BigDecimal.ZERO, amount.negate());
            subtractPairwiseDebt(debtor, buyer, group, amount);
        }
    }

    @Transactional
    public void revertGroupBalances(Group group) {
        if (group == null) {
            return;
        }

        List<UserGroupBalance> groupBalances = userGroupBalanceRepository.findByGroupId(group.getId());
        for (UserGroupBalance groupBalance : groupBalances) {
            UserBalance userBalance = userBalanceRepository.findByUserId(groupBalance.getUser().getId()).orElse(null);
            if (userBalance != null) {
                userBalance.setTotalPaid(userBalance.getTotalPaid().subtract(groupBalance.getTotalPaid()));
                userBalance.setTotalOwed(userBalance.getTotalOwed().subtract(groupBalance.getTotalOwed()));
                userBalance.setNetBalance(userBalance.getNetBalance().subtract(groupBalance.getNetBalance()));
            }
        }

        userGroupBalanceRepository.deleteByGroupId(group.getId());
        pairwiseSettlementRepository.deleteByGroupId(group.getId());
    }

    @Transactional
    public void applyPayment(Payment payment) {
        if (payment == null || payment.getPayer() == null || payment.getPayee() == null || payment.getAmount() == null) {
            return;
        }

        User payer = payment.getPayer();
        User payee = payment.getPayee();
        BigDecimal amount = payment.getAmount();
        Group group = payment.getGroup();

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("L'importo del rimborso deve essere positivo");
        }

        List<PairwiseSettlement> groupSettlements = null;
        if (group != null) {
            groupSettlements = pairwiseSettlementRepository.findByGroupIdForUpdate(group.getId());
            BigDecimal limit = calculateGroupPaymentLimit(payer.getId(), payee.getId(),
                    calculateGroupNetBalances(groupSettlements));
            if (amount.compareTo(limit) > 0) {
                throw new PaymentExceedsDebtException(
                        "Il rimborso (" + amount + ") supera il debito effettivo (" + limit + ")");
            }
        }

        // Riduce il debito del payer e il credito del payee
        updateUserBalanceForPayment(payer, group, BigDecimal.ZERO, amount.negate());
        updateUserBalanceForPayment(payee, group, amount.negate(), BigDecimal.ZERO);

        if (group == null) {
            subtractPairwiseDebt(payer, payee, null, amount);
        } else {
            applyGroupPaymentToSettlements(groupSettlements, group, payer, payee, amount);
        }
    }

    @Transactional
    public void revertPayment(Payment payment) {
        if (payment == null || payment.getPayer() == null || payment.getPayee() == null || payment.getAmount() == null) {
            return;
        }

        User payer = payment.getPayer();
        User payee = payment.getPayee();
        BigDecimal amount = payment.getAmount();
        Group group = payment.getGroup();

        // Ripristina il debito del payer e il credito del payee
        updateUserBalanceForPayment(payer, group, BigDecimal.ZERO, amount);
        updateUserBalanceForPayment(payee, group, amount, BigDecimal.ZERO);

        addPairwiseDebt(payer, payee, group, amount);
    }

    @Transactional(readOnly = true)
    public BigDecimal getDebtBetween(Long payerId, Long payeeId, Long groupId) {
        if (groupId != null) {
            return pairwiseSettlementRepository
                    .findByDebtorIdAndCreditorIdAndGroupId(payerId, payeeId, groupId)
                    .map(PairwiseSettlement::getAmount)
                    .orElse(BigDecimal.ZERO);
        }

        // groupId null: contano solo i settlement personali (senza gruppo); i debiti
        // ancora dentro i gruppi si saldano solo con rimborsi che indicano il groupId.
        return pairwiseSettlementRepository
                .findByDebtorIdAndCreditorIdAndGroupIdIsNull(payerId, payeeId)
                .map(PairwiseSettlement::getAmount)
                .orElse(BigDecimal.ZERO);
    }

    private void updateUserBalanceForPayment(User user, Group group, BigDecimal paidDelta, BigDecimal owedDelta) {
        UserBalance userBalance = userBalanceRepository.findByUserId(user.getId())
                .orElseGet(() -> createUserBalance(user));
        userBalance.setTotalPaid(userBalance.getTotalPaid().add(paidDelta));
        userBalance.setTotalOwed(userBalance.getTotalOwed().add(owedDelta));
        userBalance.setNetBalance(userBalance.getTotalPaid().subtract(userBalance.getTotalOwed()));

        if (group != null) {
            UserGroupBalance groupBalance = userGroupBalanceRepository.findByUserIdAndGroupId(user.getId(), group.getId())
                    .orElseGet(() -> createUserGroupBalance(user, group));
            groupBalance.setTotalPaid(groupBalance.getTotalPaid().add(paidDelta));
            groupBalance.setTotalOwed(groupBalance.getTotalOwed().add(owedDelta));
            groupBalance.setNetBalance(groupBalance.getTotalPaid().subtract(groupBalance.getTotalOwed()));
        }
    }

    private void updateUserBalance(User user, User buyer, Group group, BigDecimal paidDelta, BigDecimal owedDelta) {
        UserBalance userBalance = userBalanceRepository.findByUserId(user.getId())
                .orElseGet(() -> createUserBalance(user));
        userBalance.setTotalPaid(userBalance.getTotalPaid().add(paidDelta));
        userBalance.setTotalOwed(userBalance.getTotalOwed().add(owedDelta));
        userBalance.setNetBalance(userBalance.getTotalPaid().subtract(userBalance.getTotalOwed()));

        // Niente saldo di gruppo per le spese personali (group null).
        if (group != null) {
            UserGroupBalance groupBalance = userGroupBalanceRepository.findByUserIdAndGroupId(user.getId(), group.getId())
                    .orElseGet(() -> createUserGroupBalance(user, group));
            groupBalance.setTotalPaid(groupBalance.getTotalPaid().add(paidDelta));
            groupBalance.setTotalOwed(groupBalance.getTotalOwed().add(owedDelta));
            groupBalance.setNetBalance(groupBalance.getTotalPaid().subtract(groupBalance.getTotalOwed()));
        }
    }

    private UserBalance createUserBalance(User user) {
        UserBalance balance = new UserBalance();
        balance.setUser(user);
        return userBalanceRepository.save(balance);
    }

    private UserGroupBalance createUserGroupBalance(User user, Group group) {
        UserGroupBalance balance = new UserGroupBalance();
        balance.setUser(user);
        balance.setGroup(group);
        return userGroupBalanceRepository.save(balance);
    }

    private void addPairwiseDebt(User debtor, User creditor, Group group, BigDecimal amount) {
        Optional<PairwiseSettlement> inverse = findPairwiseSettlement(creditor, debtor, group);

        if (inverse.isPresent()) {
            PairwiseSettlement existing = inverse.get();
            int comparison = existing.getAmount().compareTo(amount);
            if (comparison > 0) {
                existing.setAmount(existing.getAmount().subtract(amount));
                return;
            }
            pairwiseSettlementRepository.delete(existing);
            if (comparison == 0) {
                return;
            }
            amount = amount.subtract(existing.getAmount());
        }

        PairwiseSettlement settlement = findPairwiseSettlement(debtor, creditor, group)
                .orElseGet(() -> createPairwiseSettlement(debtor, creditor, group));
        settlement.setAmount(settlement.getAmount().add(amount));
    }

    private void subtractPairwiseDebt(User debtor, User creditor, Group group, BigDecimal amount) {
        Optional<PairwiseSettlement> direct = findPairwiseSettlement(debtor, creditor, group);

        if (direct.isPresent()) {
            PairwiseSettlement existing = direct.get();
            int comparison = existing.getAmount().compareTo(amount);
            if (comparison > 0) {
                existing.setAmount(existing.getAmount().subtract(amount));
                return;
            }
            pairwiseSettlementRepository.delete(existing);
            if (comparison == 0) {
                return;
            }
            amount = amount.subtract(existing.getAmount());
            addPairwiseDebt(creditor, debtor, group, amount);
            return;
        }

        Optional<PairwiseSettlement> inverse = findPairwiseSettlement(creditor, debtor, group);
        if (inverse.isPresent()) {
            inverse.get().setAmount(inverse.get().getAmount().add(amount));
        } else {
            // Il settlement può legittimamente mancare: se la spesa ha compensato
            // esattamente un debito inverso, addPairwiseDebt ha cancellato il record
            // (netto zero). Lo storno equivale quindi a ripristinare il debito della
            // controparte, creando il settlement inverso.
            log.warn("Pairwise settlement da revert non trovato per debtor={}, creditor={}, group={}: creato il settlement inverso",
                    debtor.getId(), creditor.getId(), group != null ? group.getId() : "null");
            addPairwiseDebt(creditor, debtor, group, amount);
        }
    }

    private Optional<PairwiseSettlement> findPairwiseSettlement(User debtor, User creditor, Group group) {
        if (group == null) {
            return pairwiseSettlementRepository
                    .findByDebtorIdAndCreditorIdAndGroupIdIsNull(debtor.getId(), creditor.getId());
        }
        return pairwiseSettlementRepository
                .findByDebtorIdAndCreditorIdAndGroupId(debtor.getId(), creditor.getId(), group.getId());
    }

    private PairwiseSettlement createPairwiseSettlement(User debtor, User creditor, Group group) {
        PairwiseSettlement settlement = new PairwiseSettlement();
        settlement.setDebtor(debtor);
        settlement.setCreditor(creditor);
        settlement.setGroup(group);
        pairwiseSettlementRepository.save(settlement);
        return settlement;
    }

    private void updateGroupBalanceOnly(User user, Group group, BigDecimal paidDelta, BigDecimal owedDelta) {
        UserGroupBalance groupBalance = userGroupBalanceRepository.findByUserIdAndGroupId(user.getId(), group.getId())
                .orElseGet(() -> createUserGroupBalance(user, group));
        groupBalance.setTotalPaid(groupBalance.getTotalPaid().add(paidDelta));
        groupBalance.setTotalOwed(groupBalance.getTotalOwed().add(owedDelta));
        groupBalance.setNetBalance(groupBalance.getTotalPaid().subtract(groupBalance.getTotalOwed()));
    }

    private void applyGroupPaymentToSettlements(List<PairwiseSettlement> settlements, Group group,
            User payer, User payee, BigDecimal amount) {
        Map<SettlementKey, PairwiseSettlement> graph = new HashMap<>();
        for (PairwiseSettlement settlement : settlements) {
            graph.put(settlementKey(settlement.getDebtor(), settlement.getCreditor()), settlement);
        }

        BigDecimal remaining = amount;
        PairwiseSettlement direct = graph.get(settlementKey(payer, payee));
        if (direct != null && direct.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal delta = remaining.min(direct.getAmount());
            subtractLocalDebt(direct, delta);
            remaining = remaining.subtract(delta);
        }

        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            PairwiseSettlement payerDebt = findPositiveDebt(graph, payer.getId(), true);
            PairwiseSettlement payeeCredit = findPositiveDebt(graph, payee.getId(), false);
            if (payerDebt == null || payeeCredit == null) {
                throw new IllegalStateException("Settlement di gruppo incoerenti durante il pagamento");
            }

            User intermediateCreditor = payerDebt.getCreditor();
            User intermediateDebtor = payeeCredit.getDebtor();
            BigDecimal delta = remaining.min(payerDebt.getAmount()).min(payeeCredit.getAmount());
            subtractLocalDebt(payerDebt, delta);
            subtractLocalDebt(payeeCredit, delta);
            if (!intermediateDebtor.getId().equals(intermediateCreditor.getId())) {
                addLocalDebt(graph, group, intermediateDebtor, intermediateCreditor, delta);
            }
            remaining = remaining.subtract(delta);
        }

        for (PairwiseSettlement settlement : graph.values()) {
            if (settlement.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                if (settlement.getId() != null) {
                    pairwiseSettlementRepository.delete(settlement);
                }
            } else if (settlement.getId() == null) {
                pairwiseSettlementRepository.save(settlement);
            }
        }
    }

    private PairwiseSettlement findPositiveDebt(Map<SettlementKey, PairwiseSettlement> graph,
            Long userId, boolean outgoing) {
        return graph.values().stream()
                .filter(settlement -> settlement.getAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(settlement -> outgoing
                        ? settlement.getDebtor().getId().equals(userId)
                        : settlement.getCreditor().getId().equals(userId))
                .sorted(Comparator.comparing(settlement -> outgoing
                        ? settlement.getCreditor().getId()
                        : settlement.getDebtor().getId()))
                .findFirst()
                .orElse(null);
    }

    private void subtractLocalDebt(PairwiseSettlement settlement, BigDecimal amount) {
        settlement.setAmount(settlement.getAmount().subtract(amount));
    }

    private void addLocalDebt(Map<SettlementKey, PairwiseSettlement> graph, Group group,
            User debtor, User creditor, BigDecimal amount) {
        PairwiseSettlement inverse = graph.get(settlementKey(creditor, debtor));
        if (inverse != null && inverse.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal inverseAmount = inverse.getAmount();
            int comparison = inverseAmount.compareTo(amount);
            if (comparison > 0) {
                inverse.setAmount(inverseAmount.subtract(amount));
                return;
            }
            inverse.setAmount(BigDecimal.ZERO);
            if (comparison == 0) {
                return;
            }
            amount = amount.subtract(inverseAmount);
        }

        SettlementKey key = settlementKey(debtor, creditor);
        PairwiseSettlement settlement = graph.get(key);
        if (settlement == null) {
            settlement = new PairwiseSettlement();
            settlement.setDebtor(debtor);
            settlement.setCreditor(creditor);
            settlement.setGroup(group);
            settlement.setAmount(BigDecimal.ZERO);
            graph.put(key, settlement);
        }
        settlement.setAmount(settlement.getAmount().add(amount));
    }

    private SettlementKey settlementKey(User debtor, User creditor) {
        return new SettlementKey(debtor.getId(), creditor.getId());
    }

    private record SettlementKey(Long debtorId, Long creditorId) {
    }

    private Map<User, BigDecimal> calculateGroupNetBalances(List<PairwiseSettlement> settlements) {
        Map<User, BigDecimal> netBalances = new HashMap<>();
        for (PairwiseSettlement settlement : settlements) {
            netBalances.merge(settlement.getDebtor(), settlement.getAmount().negate(), BigDecimal::add);
            netBalances.merge(settlement.getCreditor(), settlement.getAmount(), BigDecimal::add);
        }
        return netBalances;
    }

    private BigDecimal calculateGroupPaymentLimit(Long payerId, Long payeeId,
            Map<User, BigDecimal> netBalances) {
        BigDecimal payerNet = netBalances.entrySet().stream()
                .filter(entry -> entry.getKey().getId().equals(payerId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal payeeNet = netBalances.entrySet().stream()
                .filter(entry -> entry.getKey().getId().equals(payeeId))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal payerDebt = payerNet.negate().max(BigDecimal.ZERO);
        BigDecimal payeeCredit = payeeNet.max(BigDecimal.ZERO);
        return payerDebt.min(payeeCredit);
    }

    private String scopeKey(PairwiseSettlement settlement, Long userId) {
        Long groupId = settlement.getGroup() != null ? settlement.getGroup().getId() : null;
        return counterpartyId(settlement, userId) + "|" + groupId;
    }

    private Long counterpartyId(PairwiseSettlement settlement, Long userId) {
        return settlement.getDebtor().getId().equals(userId)
                ? settlement.getCreditor().getId()
                : settlement.getDebtor().getId();
    }

    // Segno dal punto di vista dell'utente: positivo = debito verso la controparte.
    private BigDecimal signedAmount(PairwiseSettlement settlement, Long userId) {
        return settlement.getDebtor().getId().equals(userId)
                ? settlement.getAmount()
                : settlement.getAmount().negate();
    }

    private UserSettlementDTO toUserSettlementDto(Long counterpartyId, BigDecimal net, Group group) {
        if (net.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        User counterparty = userRepository.findById(counterpartyId)
                .orElseThrow(() -> new EntityNotFoundException("Utente non trovato"));
        Long groupId = group != null ? group.getId() : null;
        String groupName = group != null ? group.getName() : null;
        if (net.compareTo(BigDecimal.ZERO) > 0) {
            return new UserSettlementDTO(new UserDTO(counterparty), net, SettlementDirection.DEBT, groupId, groupName);
        }
        return new UserSettlementDTO(new UserDTO(counterparty), net.negate(), SettlementDirection.CREDIT, groupId,
                groupName);
    }
}
