package it.javaWS.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.ShoppingItemDTO;
import it.javaWS.models.entities.User;
import it.javaWS.services.ShoppingItemService;

@RestController
@RequestMapping("/shopping-items")
@SecurityRequirement(name = "bearerAuth")
public class ShoppingItemController {

	private final ShoppingItemService shoppingItemService;

	public ShoppingItemController(ShoppingItemService shoppingItemService) {
		this.shoppingItemService = shoppingItemService;
	}

	@Operation(summary = "Recupera la lista della spesa di un gruppo", description = "Restituisce gli articoli del gruppo con paginazione: prima quelli da acquistare, in fondo gli acquistati. Con toBuy si filtrano solo gli attivi (true) o solo gli acquistati (false).")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Lista articoli restituita"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "403", description = "L'utente non fa parte del gruppo") })
	@GetMapping("/group/{groupId}")
	public ResponseEntity<Page<ShoppingItemDTO>> getItemsByGroup(@AuthenticationPrincipal User user,
			@PathVariable Long groupId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) Boolean toBuy) {
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(shoppingItemService.getItemsByGroupDto(groupId, user.getId(), toBuy, pageable));
	}

	@Operation(summary = "Aggiunge un articolo alla lista della spesa", description = "Crea un articolo da acquistare nel gruppo. Il nome viene trimmato e deve essere univoco nel gruppo (case-insensitive), anche rispetto agli articoli già acquistati.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Articolo creato con successo"),
			@ApiResponse(responseCode = "400", description = "Dati non validi o articolo già presente in lista"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "403", description = "L'utente non fa parte del gruppo") })
	@PostMapping("/new")
	public ResponseEntity<ShoppingItemDTO> createItem(@AuthenticationPrincipal User user,
			@RequestParam Long groupId,
			@RequestParam String name,
			@RequestParam(required = false) String note) {
		return ResponseEntity.ok(shoppingItemService.createItemDto(groupId, user.getId(), name, note));
	}

	@Operation(summary = "Aggiorna lo stato di un articolo", description = "Toggle della checkbox \"da acquistare\": libero in entrambi i sensi.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Articolo aggiornato"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "403", description = "L'utente non fa parte del gruppo"),
			@ApiResponse(responseCode = "404", description = "Articolo non trovato") })
	@PutMapping("/{id}")
	public ResponseEntity<ShoppingItemDTO> updateToBuy(@AuthenticationPrincipal User user,
			@PathVariable Long id,
			@RequestParam boolean toBuy) {
		return ResponseEntity.ok(shoppingItemService.updateToBuyDto(id, user.getId(), toBuy));
	}

	@Operation(summary = "Elimina un articolo", description = "Rimuove un articolo dalla lista della spesa del gruppo")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Articolo eliminato"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato"),
			@ApiResponse(responseCode = "403", description = "L'utente non fa parte del gruppo"),
			@ApiResponse(responseCode = "404", description = "Articolo non trovato") })
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteItem(@AuthenticationPrincipal User user, @PathVariable Long id) {
		shoppingItemService.deleteItem(id, user.getId());
		return ResponseEntity.ok().build();
	}
}
