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

    @Transactional(readOnly = true)
    public Page<PaymentDTO> getPaymentsForUser(Long userId, Pageable pageable) {
        return paymentRepository.findByPayerIdOrPayeeId(userId, userId, pageable)
                .map(PaymentDTO::new);
    }
}
