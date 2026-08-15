package it.javaWS.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import it.javaWS.repositories.PairwiseSettlementRepository;
import it.javaWS.repositories.TransactionRepository;
import it.javaWS.repositories.UserBalanceRepository;
import it.javaWS.repositories.UserGroupBalanceRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.InvalidPaymentException;
import it.javaWS.utils.PaymentExceedsDebtException;
import jakarta.persistence.EntityNotFoundException;

@Service
public class BalanceService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final UserGroupBalanceRepository userGroupBalanceRepository;
    private final PairwiseSettlementRepository pairwiseSettlementRepository;

    public BalanceService(TransactionRepository transactionRepository, UserRepository userRepository,
            UserBalanceRepository userBalanceRepository,
            UserGroupBalanceRepository userGroupBalanceRepository,
            PairwiseSettlementRepository pairwiseSettlementRepository) {
        this.transactionRepository = transactionRepository;
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

    // Debiti pendenti del gruppo letti dai settlement pairwise: riflettono spese,
    // rimborsi e trasferimenti a livello globale dovuti all'uscita di membri.
    @Transactional(readOnly = true)
    public List<SettlementDTO> getGroupSettlementStatus(Long groupId) {
        return pairwiseSettlementRepository.findByGroupId(groupId).stream()
                .map(ps -> new SettlementDTO(new UserDTO(ps.getDebtor()), new UserDTO(ps.getCreditor()),
                        ps.getAmount()))
                .toList();
    }

    /**
     * Trasferisce a livello globale (group = null) tutti i debiti/crediti che l'utente
     * ha nel gruppo, riallineando i saldi di gruppo. Invocato quando l'utente esce
     * dal gruppo: i suoi rapporti economici con gli altri membri diventano personali.
     */
    @Transactional
    public void transferUserSettlementsToGlobal(Group group, Long userId) {
        for (PairwiseSettlement settlement : pairwiseSettlementRepository.findByGroupId(group.getId())) {
            boolean isDebtor = settlement.getDebtor().getId().equals(userId);
            boolean isCreditor = settlement.getCreditor().getId().equals(userId);
            if (!isDebtor && !isCreditor) {
                continue;
            }
            User debtor = settlement.getDebtor();
            User creditor = settlement.getCreditor();
            BigDecimal amount = settlement.getAmount();

            // Sposta il debito: fuori dallo scope gruppo, dentro quello globale (con netting).
            subtractPairwiseDebt(debtor, creditor, group, amount);
            addPairwiseDebt(debtor, creditor, null, amount);

            // Riallinea solo i saldi di gruppo (come un rimborso interno): quelli globali
            // restano invariati perché il debito esiste ancora, è solo cambiato di scope.
            updateGroupBalanceOnly(debtor, group, BigDecimal.ZERO, amount.negate());
            updateGroupBalanceOnly(creditor, group, amount.negate(), BigDecimal.ZERO);
        }
    }

    @Transactional
    public void applyBill(Bill bill) {
        if (bill == null || bill.getBuyer() == null || bill.getGroup() == null) {
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
        if (bill == null || bill.getBuyer() == null || bill.getGroup() == null) {
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

        // Riduce il debito del payer e il credito del payee
        updateUserBalanceForPayment(payer, group, BigDecimal.ZERO, amount.negate());
        updateUserBalanceForPayment(payee, group, amount.negate(), BigDecimal.ZERO);

        subtractPairwiseDebt(payer, payee, group, amount);
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

        UserGroupBalance groupBalance = userGroupBalanceRepository.findByUserIdAndGroupId(user.getId(), group.getId())
                .orElseGet(() -> createUserGroupBalance(user, group));
        groupBalance.setTotalPaid(groupBalance.getTotalPaid().add(paidDelta));
        groupBalance.setTotalOwed(groupBalance.getTotalOwed().add(owedDelta));
        groupBalance.setNetBalance(groupBalance.getTotalPaid().subtract(groupBalance.getTotalOwed()));
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
            throw new IllegalStateException("Pairwise settlement da revert non trovato per debtor=" + debtor.getId()
                    + ", creditor=" + creditor.getId() + ", group=" + (group != null ? group.getId() : "null"));
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
