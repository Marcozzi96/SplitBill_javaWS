package it.javaWS.models.dto;

import it.javaWS.models.entities.ShoppingItem;
import lombok.Data;

/**
 * DTO leggero che esporta solo l'id di un articolo e la sua posizione ordinata.
 * Usato nella risposta del reorder per consentire al FE di allinearsi senza una GET extra.
 */
@Data
public class ShoppingItemPositionDTO {

	private Long itemId;
	private double position;

	public ShoppingItemPositionDTO(ShoppingItem item) {
		this.itemId = item.getId();
		this.position = item.getPosition();
	}

	public ShoppingItemPositionDTO(Long itemId, double position) {
		this.itemId = itemId;
		this.position = position;
	}
}
