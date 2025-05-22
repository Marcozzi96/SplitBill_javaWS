package it.javaWS.javaws.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import it.javaWS.javaws.models.Bill;
import lombok.Data;

@JsonPropertyOrder({
	"billId",
	"creationDate",
	"description",
	"notes",
	"groupId",
	"amount",
	"buyer",
	"transactions"
})
@Data
public class BillDTO {
	
	private Long billId;

	private String description;
	private LocalDate creationDate;
	private BigDecimal amount;
	private String notes;
	private UserDTO buyer;
	private Long groupId;
	private Set<TransactionDTO> transactions;

	public BillDTO(Bill bill) {
		this.billId = bill.getId();
		this.description = bill.getDescription();
		this.amount = bill.getAmount();
		this.notes = bill.getNotes();
		this.creationDate = bill.getDate();

		this.buyer = new UserDTO(bill.getBuyer());
		this.groupId = bill.getGroup().getId();

		

	}


}
