package it.javaWS.controllers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.BillDTO;
import it.javaWS.models.dto.UserDTO;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.services.BillService;
import it.javaWS.services.GroupService;
import it.javaWS.services.UserService;
import it.javaWS.utils.UnauthorizedAccessException;

@RestController
@RequestMapping("/bills")
@SecurityRequirement(name = "bearerAuth")
public class BillController {

	private final BillService billService;
	private final UserService userService;
	private final GroupService groupService;

	public BillController(BillService billService, UserService userService, GroupService groupService) {
		this.billService = billService;
		this.userService = userService;
		this.groupService = groupService;
	}

	@Operation(summary = "Crea una nuova spesa", description = "Crea una spesa con suddivisione personalizzata dei debiti. La somma dei debiti deve essere esattamente uguale all'importo totale.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Spesa creata con successo"),
			@ApiResponse(responseCode = "400", description = "Dati non validi (importo negativo o somma debiti diversa dall'importo)"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@PostMapping("/new")
	public ResponseEntity<BillDTO> createBill(@AuthenticationPrincipal User user, @RequestParam String description,
			@RequestParam BigDecimal amount, @RequestParam String notes, @RequestParam Long groupId,
			@RequestBody Map<Long, BigDecimal> usersDebit) {

		if (BigDecimal.ZERO.compareTo(amount) > 0)
			throw new IllegalArgumentException("Importo non valido");

		if (!groupService.existsByGroupIdAndUserId(groupId, user.getId())) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}

		Set<UserGroup> userGroups = groupService.getUserGroup(groupId, usersDebit.keySet());
		if (userGroups.size() != usersDebit.size()) {
			throw new IllegalArgumentException("Non tutti i debitori fanno parte del gruppo");
		}
		Set<User> clients = userGroups.stream().map(UserGroup::getUser).collect(Collectors.toSet());

		User buyer = userService.getUser(user.getId())
				.orElseThrow(() -> new IllegalStateException("Utente non trovato"));

		Map<User, BigDecimal> usersDebitConvertito = new HashMap<>();
		for (User debtor : clients) {
			usersDebitConvertito.put(debtor, usersDebit.get(debtor.getId()));
		}

		return ResponseEntity.ok(billService.createBillDto(description, amount, notes, buyer, groupService.getGroup(groupId), usersDebitConvertito));
	}

	@Operation(summary = "Recupera le spese di un gruppo", description = "Restituisce le spese del gruppo con paginazione")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Lista spese restituita"),
			@ApiResponse(responseCode = "401", description = "Accesso non autorizzato") })
	@GetMapping("/group/{groupId}")
	public ResponseEntity<Page<BillDTO>> getBillsByGroup(@AuthenticationPrincipal User user,
			@PathVariable Long groupId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		if (!groupService.existsByGroupIdAndUserId(groupId, user.getId())) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(billService.getBillsByGroup(groupId, pageable));
	}

	@Operation(summary = "Recupera le spese dove l'utente è buyer", description = "Restituisce le spese create dall'utente autenticato con paginazione")
	@ApiResponse(responseCode = "200", description = "Lista spese restituita")
	@GetMapping("/getWhereImBuyer")
	public ResponseEntity<Page<BillDTO>> getBillsWhereUserIsBuyer(@AuthenticationPrincipal User user,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(billService.getBillsWhereUserIsBuyer(user.getId(), pageable));
	}

	@Operation(summary = "Recupera le proprie spese", description = "Restituisce tutte le spese in cui l'utente autenticato è coinvolto, con paginazione")
	@ApiResponse(responseCode = "200", description = "Lista spese restituita")
	@GetMapping("/getMyBills")
	public ResponseEntity<Page<BillDTO>> getBillsByUser(@AuthenticationPrincipal User user,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		Pageable pageable = PageRequest.of(page, size);
		return ResponseEntity.ok(billService.getBillsByUserId(user.getId(), pageable));
	}

	@Operation(summary = "Modifica una spesa", description = "Consente la modifica solo al buyer della spesa o all'admin del gruppo")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Spesa modificata"),
			@ApiResponse(responseCode = "400", description = "Dati non validi"),
			@ApiResponse(responseCode = "401", description = "Non autorizzato"),
			@ApiResponse(responseCode = "404", description = "Spesa non trovata") })
	@PutMapping("/{id}")
	public ResponseEntity<BillDTO> updateBill(@AuthenticationPrincipal User user, @PathVariable Long id,
			@RequestParam String description, @RequestParam BigDecimal amount, @RequestParam String notes,
			@RequestBody Map<Long, BigDecimal> usersDebit) {
		return ResponseEntity.ok(billService.updateBill(id, user.getId(), description, amount, notes, usersDebit));
	}

	@Operation(summary = "Elimina una spesa", description = "Consente l'eliminazione solo al buyer della spesa o all'admin del gruppo")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Spesa eliminata"),
			@ApiResponse(responseCode = "401", description = "Non autorizzato"),
			@ApiResponse(responseCode = "404", description = "Spesa non trovata") })
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteBill(@AuthenticationPrincipal User user, @PathVariable Long id) {
		BillDTO bill = billService.getBillDto(id);
		boolean isBuyer = bill.getBuyer().getUserId().equals(user.getId());
		boolean isAdmin = groupService.isUserAdminOfGroup(bill.getGroupId(), user.getId());
		if (!isBuyer && !isAdmin) {
			throw new UnauthorizedAccessException("Non sei autorizzato a eliminare questa spesa");
		}
		billService.deleteBill(id);
		return ResponseEntity.ok().build();
	}
}
