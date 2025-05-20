package it.javaWS.javaws.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import it.javaWS.javaws.models.Bill;
import lombok.Data;

@Data
public class BillDTO {
	private Long BillId;

	private String description;
	private LocalDate creationDate;
	private BigDecimal amount;
	private String notes;
	private UserDTO buyer;
	private Long groupId;
	private Set<TransactionDTO> transactions;

	public BillDTO(Bill bill) {
		this.BillId = bill.getId();
		this.description = bill.getDescription();
		this.amount = bill.getAmount();
		this.notes = bill.getNotes();
		this.creationDate = bill.getDate();

		this.buyer = new UserDTO(bill.getBuyer());
		this.groupId = bill.getGroup().getId();

		

	}


}
