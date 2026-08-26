package it.javaWS.services;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.javaWS.models.dto.PaymentDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Payment;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.PaymentRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.InvalidPaymentException;
import it.javaWS.utils.PaymentExceedsDebtException;
import it.javaWS.utils.UnauthorizedAccessException;
import it.javaWS.utils.UserNotFoundException;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final BalanceService balanceService;

    public PaymentService(PaymentRepository paymentRepository, UserRepository userRepository,
            GroupService groupService, BalanceService balanceService) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.balanceService = balanceService;
    }

    @Transactional
    public PaymentDTO createPayment(Long payerId, Long payeeId, BigDecimal amount, Long groupId,
            String notes, Long authenticatedUserId) {

        if (!payerId.equals(authenticatedUserId)) {
            throw new UnauthorizedAccessException("Puoi registrare solo rimborsi da te effettuati");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("L'importo del rimborso deve essere positivo");
        }

        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> new UserNotFoundException("Pagatore non trovato"));
        User payee = userRepository.findById(payeeId)
                .orElseThrow(() -> new UserNotFoundException("Beneficiario non trovato"));

        Group group = null;
        if (groupId != null) {
            group = groupService.getGroup(groupId);
            if (group == null || !groupService.existsByGroupIdAndUserId(groupId, payerId)
                    || !groupService.existsByGroupIdAndUserId(groupId, payeeId)) {
                throw new InvalidPaymentException("Gruppo non valido o utenti non membri");
            }
        }

        BigDecimal debt = balanceService.getDebtBetween(payerId, payeeId, groupId);
        if (amount.compareTo(debt) > 0) {
            throw new PaymentExceedsDebtException("Il rimborso (" + amount + ") supera il debito effettivo (" + debt + ")");
        }

        Payment payment = new Payment();
        payment.setPayer(payer);
        payment.setPayee(payee);
        payment.setGroup(group);
        payment.setAmount(amount);
        payment.setDate(LocalDate.now());
        payment.setNotes(notes);

        Payment saved = paymentRepository.save(payment);
        balanceService.applyPayment(saved);

        return new PaymentDTO(saved);
    }

    // "Dimentica" il debito di un utente eliminato verso il creditore: registra un
    // rimborso fittizio pari al debito residuo, azzerandolo. Il payer non deve più
    // essere membro attivo del gruppo (potrebbe esserne uscito prima dell'eliminazione).
    @Transactional
    public PaymentDTO forgiveDebt(Long creditorId, Long payerId, Long groupId) {
        User payer = userRepository.findById(payerId)
                .orElseThrow(() -> new UserNotFoundException("Pagatore non trovato"));
        if (!payer.isDeleted()) {
            throw new InvalidPaymentException("Puoi dimenticare solo i debiti di utenti eliminati");
        }
        User creditor = userRepository.findById(creditorId)
                .orElseThrow(() -> new UserNotFoundException("Creditore non trovato"));

        Group group = null;
        if (groupId != null) {
            group = groupService.getGroup(groupId);
            if (group == null || !groupService.existsByGroupIdAndUserId(groupId, creditorId)) {
                throw new InvalidPaymentException("Gruppo non valido o utente non membro");
            }
        }

        BigDecimal debt = balanceService.getDebtBetween(payerId, creditorId, groupId);
        if (debt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidPaymentException("Nessun debito da dimenticare");
        }

        Payment payment = new Payment();
        payment.setPayer(payer);
        payment.setPayee(creditor);
        payment.setGroup(group);
        payment.setAmount(debt);
        payment.setDate(LocalDate.now());
        payment.setNotes("Debito dimenticato (utente eliminato)");

        Payment saved = paymentRepository.save(payment);
        balanceService.applyPayment(saved);

        return new PaymentDTO(saved);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDTO> getPaymentsForUser(Long userId, Pageable pageable) {
        return paymentRepository.findByPayerIdOrPayeeIdOrderByDateDescIdDesc(userId, userId, pageable)
                .map(PaymentDTO::new);
    }
}
