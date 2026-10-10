package it.javaWS.models.dto;

import lombok.Data;

/**
 * Richiesta di spostamento manuale di un articolo della lista della spesa.
 */
@Data
public class ReorderShoppingItemRequest {

	private Long prevItemId;
	private Long nextItemId;
}
