package it.javaWS.models.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import it.javaWS.models.entities.ShoppingItem;
import lombok.Data;

@JsonPropertyOrder({
	"itemId",
	"groupId",
	"name",
	"note",
	"toBuy",
	"createdAt"
})
@Data
public class ShoppingItemDTO {

	private Long itemId;
	private Long groupId;
	private String name;
	private String note;
	private boolean toBuy;
	private LocalDateTime createdAt;

	public ShoppingItemDTO(ShoppingItem item) {
		this.itemId = item.getId();
		// Va letto dentro la transazione: group è un proxy lazy.
		this.groupId = item.getGroup().getId();
		this.name = item.getName();
		this.note = item.getNote();
		this.toBuy = item.isToBuy();
		this.createdAt = item.getCreatedAt();
	}
}
