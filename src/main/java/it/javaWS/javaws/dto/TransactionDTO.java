package it.javaWS.javaws.dto;

import java.math.BigDecimal;

import it.javaWS.javaws.models.Transaction;
import lombok.Data;

@Data
public class TransactionDTO {
	private Long id;
	private BigDecimal amount;
	private Long userId;
	
	public TransactionDTO(Transaction transaction) {
		this.id = transaction.getId();
		this.amount = transaction.getAmount();
		this.userId = transaction.getUser().getId();
	}
}



