package it.javaWS.javaws.dto;

import java.math.BigDecimal;

import it.javaWS.javaws.models.Transaction;
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



