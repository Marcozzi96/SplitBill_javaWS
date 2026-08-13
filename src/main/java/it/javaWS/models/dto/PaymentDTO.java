package it.javaWS.models.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.javaWS.models.entities.Payment;
import lombok.Data;

@Data
public class PaymentDTO {

    private Long paymentId;
    private UserDTO payer;
    private UserDTO payee;
    private Long groupId;
    private BigDecimal amount;
    private LocalDate date;
    private String notes;

    public PaymentDTO(Payment payment) {
        this.paymentId = payment.getId();
        this.payer = new UserDTO(payment.getPayer());
        this.payee = new UserDTO(payment.getPayee());
        this.groupId = payment.getGroup() != null ? payment.getGroup().getId() : null;
        this.amount = payment.getAmount();
        this.date = payment.getDate();
        this.notes = payment.getNotes();
    }
}
