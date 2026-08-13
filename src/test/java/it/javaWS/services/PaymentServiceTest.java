package it.javaWS.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import it.javaWS.models.dto.PaymentDTO;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.Payment;
import it.javaWS.models.entities.User;
import it.javaWS.repositories.PaymentRepository;
import it.javaWS.repositories.UserRepository;
import it.javaWS.utils.InvalidPaymentException;
import it.javaWS.utils.PaymentExceedsDebtException;
import it.javaWS.utils.UnauthorizedAccessException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPayment_success() {
        User payer = createUser(1L);
        User payee = createUser(2L);
        Group group = createGroup(10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(payer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(payee));
        when(groupService.getGroup(10L)).thenReturn(group);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupService.existsByGroupIdAndUserId(10L, 2L)).thenReturn(true);
        when(balanceService.getDebtBetween(1L, 2L, 10L)).thenReturn(new BigDecimal("50"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(100L);
            return payment;
        });

        PaymentDTO dto = paymentService.createPayment(1L, 2L, new BigDecimal("30"), 10L, "note", 1L);

        assertThat(dto.getPaymentId()).isEqualTo(100L);
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("30"));
        assertThat(dto.getPayer().getUserId()).isEqualTo(1L);
        assertThat(dto.getPayee().getUserId()).isEqualTo(2L);
        assertThat(dto.getGroupId()).isEqualTo(10L);
        verify(balanceService).applyPayment(any(Payment.class));
    }

    @Test
    void createPayment_notPayer_throwsUnauthorized() {
        assertThatThrownBy(() -> paymentService.createPayment(1L, 2L, new BigDecimal("30"), null, "note", 99L))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void createPayment_negativeAmount_throwsInvalidPayment() {
        assertThatThrownBy(() -> paymentService.createPayment(1L, 2L, new BigDecimal("-10"), null, "note", 1L))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void createPayment_exceedsDebt_throwsPaymentExceedsDebt() {
        User payer = createUser(1L);
        User payee = createUser(2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(payer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(payee));
        when(balanceService.getDebtBetween(1L, 2L, null)).thenReturn(new BigDecimal("20"));

        assertThatThrownBy(() -> paymentService.createPayment(1L, 2L, new BigDecimal("30"), null, "note", 1L))
                .isInstanceOf(PaymentExceedsDebtException.class);
    }

    @Test
    void createPayment_groupMissingMember_throwsInvalidPayment() {
        User payer = createUser(1L);
        User payee = createUser(2L);
        Group group = createGroup(10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(payer));
        when(userRepository.findById(2L)).thenReturn(Optional.of(payee));
        when(groupService.getGroup(10L)).thenReturn(group);
        when(groupService.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);
        when(groupService.existsByGroupIdAndUserId(10L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> paymentService.createPayment(1L, 2L, new BigDecimal("10"), 10L, "note", 1L))
                .isInstanceOf(InvalidPaymentException.class);
    }

    @Test
    void getPaymentsForUser_returnsPage() {
        User payer = createUser(1L);
        User payee = createUser(2L);
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPayer(payer);
        payment.setPayee(payee);
        payment.setAmount(new BigDecimal("10"));

        when(paymentRepository.findByPayerIdOrPayeeId(1L, 1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(java.util.List.of(payment)));

        Page<PaymentDTO> page = paymentService.getPaymentsForUser(1L, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().getPaymentId()).isEqualTo(1L);
    }

    private User createUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setEmail("user" + id + "@example.com");
        user.setPassword("password");
        return user;
    }

    private Group createGroup(Long id) {
        Group group = new Group();
        group.setId(id);
        group.setName("group" + id);
        return group;
    }
}
