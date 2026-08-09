package it.javaWS.controllers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.BillDTO;
import it.javaWS.models.dto.TransactionDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
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

		Group group = groupService.getGroup(groupId);
		if (group == null || !groupService.existsByGroupIdAndUserId(groupId, user.getId())) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}

		Set<UserGroup> userGroups = groupService.getUserGroup(groupId, usersDebit.keySet());
		if (userGroups.size() != usersDebit.size()) {
			throw new IllegalArgumentException("Non tutti i debitori fanno parte del gruppo");
		}
		Set<User> clients = userGroups.stream().map(UserGroup::getUser).collect(Collectors.toSet());

		Optional<User> buyerOpt = userService.getUser(user.getId());
		if (buyerOpt.isEmpty())
			throw new IllegalStateException("Utente non trovato");

		User buyer = buyerOpt.get();

		Map<User, BigDecimal> usersDebitConvertito = new HashMap<>();
		for (User debtor : clients) {
			usersDebitConvertito.put(debtor, usersDebit.get(debtor.getId()));
		}
		Bill bill = billService.createBill(description, amount, notes, buyer, group, usersDebitConvertito);

		BillDTO dto = new BillDTO(bill);
		Set<TransactionDTO> transactions = billService.getTransactionsByBillId(bill.getId()).stream()
				.map(TransactionDTO::new).collect(Collectors.toSet());
		dto.setTransactions(transactions);

		return ResponseEntity.ok(dto);
	}

	@GetMapping("/group/{groupId}")
	public ResponseEntity<List<BillDTO>> getBillsByGroup(@AuthenticationPrincipal User user, @PathVariable Long groupId) {
		if (!groupService.existsByGroupIdAndUserId(groupId, user.getId())) {
			throw new AccessDeniedException("L'utente non fa parte del gruppo richiesto");
		}
		List<BillDTO> bills = billService.getBillsByGroup(groupId).stream().map(BillDTO::new).toList();
		return ResponseEntity.ok(bills);
	}

	@GetMapping("/getWhereImBuyer")
	public ResponseEntity<List<BillDTO>> getBillsWhereUserIsBuyer(@AuthenticationPrincipal User user) {
		List<Bill> bills = billService.getBillsWhereUserIsBuyer(user.getId());

		List<BillDTO> dtoList = bills.stream().map(b -> {
			Set<TransactionDTO> transactions = billService.getTransactionsByBillId(b.getId()).stream()
					.map(TransactionDTO::new).collect(Collectors.toSet());

			BillDTO dto = new BillDTO(b);
			dto.setTransactions(transactions);
			return dto;
		}).toList();

		return ResponseEntity.ok(dtoList);
	}

	@GetMapping("/getMyBills")
	public ResponseEntity<List<BillDTO>> getBillsByUser(@AuthenticationPrincipal User user) {
		List<BillDTO> dtoList = billService.getBillsByUserId(user.getId()).stream().map(b -> {
			Set<TransactionDTO> transactions = billService.getTransactionsByBillId(b.getId()).stream()
					.map(TransactionDTO::new).collect(Collectors.toSet());
			BillDTO dto = new BillDTO(b);
			dto.setTransactions(transactions);
			return dto;
		}).toList();

		return ResponseEntity.ok(dtoList);
	}

	@Operation(summary = "Elimina una spesa", description = "Consente l'eliminazione solo al buyer della spesa o all'admin del gruppo")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Spesa eliminata"),
			@ApiResponse(responseCode = "401", description = "Non autorizzato"),
			@ApiResponse(responseCode = "404", description = "Spesa non trovata") })
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteBill(@AuthenticationPrincipal User user, @PathVariable Long id) {
		Bill bill = billService.getBill(id);
		boolean isBuyer = bill.getBuyer().getId().equals(user.getId());
		boolean isAdmin = groupService.isUserAdminOfGroup(bill.getGroup().getId(), user.getId());
		if (!isBuyer && !isAdmin) {
			throw new UnauthorizedAccessException("Non sei autorizzato a eliminare questa spesa");
		}
		billService.deleteBill(id);
		return ResponseEntity.ok().build();
	}
}
