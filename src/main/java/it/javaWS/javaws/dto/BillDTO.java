package it.javaWS.javaws.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import it.javaWS.javaws.models.Bill;
import lombok.Data;

@Data
public class BillDTO {
	private Long id;

    private String description;
    private LocalDate creationDate;
    private BigDecimal amount;
    private String notes;
    private UserDTO buyer;
    private GroupDTO group;
    
    public BillDTO(Bill bill) {
    	this.id = bill.getId();
    	this.description = bill.getDescription();
    	this.amount = bill.getAmount();
    	this.notes = bill.getNotes();
    	this.creationDate = bill.getDate();
    	
    	this.buyer = new UserDTO(bill.getBuyer());
    	this.group = new GroupDTO(bill.getGroup());
    }
}
