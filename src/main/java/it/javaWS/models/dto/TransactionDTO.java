package it.javaWS.models.dto;

import java.math.BigDecimal;

import it.javaWS.models.entities.Transaction;
import lombok.Data;

@Data
public class TransactionDTO {
	private Long transactionId;
	private BigDecimal amount;
	private Long userId;
	
	public TransactionDTO(Transaction transaction) {
		this.transactionId = transaction.getId();
		this.amount = transaction.getAmount();
		this.userId = transaction.getUser().getId();
	}
}



