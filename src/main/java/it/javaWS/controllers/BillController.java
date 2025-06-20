package it.javaWS.controllers;

import it.javaWS.models.dto.BillDTO;
import it.javaWS.models.dto.TransactionDTO;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Group;
import it.javaWS.models.entities.User;
import it.javaWS.models.entities.UserGroup;
import it.javaWS.repositories.UserGroupRepository;
import it.javaWS.services.BillService;
import it.javaWS.services.GroupService;
import it.javaWS.services.UserService;
import it.javaWS.utils.JwtUtil;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/bills")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public class BillController {

	private final BillService billService;
	private final UserService userService;
	private final GroupService groupService;
	private final UserGroupRepository userGroupRepository;
	private final JwtUtil jwtUtil;

	public BillController(BillService billService, UserService userService, GroupService groupService,
			JwtUtil jwtUtil, UserGroupRepository userGroupRepository) {
		this.billService = billService;
		this.userService = userService;
		this.groupService = groupService;
		this.userGroupRepository = userGroupRepository;
		this.jwtUtil = jwtUtil;
	}

	@PostMapping("/new")
	public ResponseEntity<?> createBill(@RequestParam String description, @RequestParam BigDecimal amount,
			@RequestParam String notes, @RequestParam Long buyerId, @RequestParam Long groupId,
			@RequestBody Map<Long, BigDecimal> usersDebit) {

		if (BigDecimal.ZERO.compareTo(amount) > 0)
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "")); // amount è vuoto, 0 o
																							// negativo
		Optional<User> buyerOpt = userService.getUser(buyerId);

		Group group = groupService.getGroup(groupId);

		Set<UserGroup> userGroups = groupService.getUserGroup(groupId, usersDebit.keySet());

		// Set<UserGroup> userGroups =
		// userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, usersDebit.keySet());
		if (userGroups.size() != usersDebit.size()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(Map.of("error", "Non tutti i debitori fanno parte del gruppo")); // NON TUTTI I CLIENTS FANNO
																							// PARTE DEL GRUPPO
		}
		Set<User> clients = userGroups.stream().map(ug -> ug.getUser()).collect(Collectors.toSet());

		if (buyerOpt.isEmpty() || group == null)
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "buyerId o groupId non validi")); // buyerId
																														// o
																														// groupId
																														// non
																														// validi

		User buyer = buyerOpt.get();

		Map<User, BigDecimal> usersDebitConvertito = new HashMap<User, BigDecimal>();

		for (User user : clients) {
			usersDebitConvertito.put(user, usersDebit.get(user.getId()));
		}
		Bill bill = billService.createBill(description, amount, notes, buyer, group, usersDebitConvertito);

		BillDTO dto = new BillDTO(bill);
		Set<TransactionDTO> transactions = billService.getTransactionsByBillId(bill.getId()).stream()
				.map(t -> new TransactionDTO(t)).collect(Collectors.toSet());
		dto.setTransactions(transactions);

		return ResponseEntity.ok(dto);
	}

	@GetMapping("/group/{groupId}")
	public ResponseEntity<?> getBillsByGroup(@RequestHeader("Authorization") String authHeader, @PathVariable Long groupId) {
		//TODO: controllare che l'utente faccia parte del gruppo
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		User user = userService.getUser(jwtUtil.extractUserId(token)).orElse(null);
		if (user == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token non valido"));
		}
		
		if(!userGroupRepository.existsByGroupIdAndUserId(groupId, user.getId())) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(Map.of("error", "L'utente non fa parte del gruppo richiesto"));
		}
		List<BillDTO> bills = billService.getBillsByGroup(groupId).stream().map(b->new BillDTO(b)).toList();
		

		return ResponseEntity.ok(bills);
	}

	@GetMapping("/getWhereImBuyer")
	public ResponseEntity<?> getBillsWhereUserIsBuyer(@RequestHeader("Authorization") String authHeader) {

		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		User user = userService.getUser(jwtUtil.extractUserId(token)).orElse(null);
		if (user == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token non valido"));
		}

		List<Bill> bills = billService.getBillsWhereUserIsBuyer(user.getId());

		List<BillDTO> dtoList = bills.stream().map(b -> {

			Set<TransactionDTO> transactions = billService.getTransactionsByBillId(b.getId()).stream()
					.map(t -> new TransactionDTO(t)).collect(Collectors.toSet());

			BillDTO dto = new BillDTO(b);
			dto.setTransactions(transactions);
			return dto;
		}).toList();

		return ResponseEntity.ok(dtoList);

	}
	
	@GetMapping("/getMyBills")
	public ResponseEntity<?> getBillsByUser(@RequestHeader("Authorization") String authHeader) {
		
		String token = authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;

		User user = userService.getUser(jwtUtil.extractUserId(token)).orElse(null);
		if (user == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Token non valido"));
		}
		
		List<BillDTO> dtoList = billService.getBillsByUserId(user.getId()).stream().map(b->{
			Set<TransactionDTO> transactions = billService.getTransactionsByBillId(b.getId()).stream()
					.map(t -> new TransactionDTO(t)).collect(Collectors.toSet());
			BillDTO dto = new BillDTO(b);
			dto.setTransactions(transactions);
			return dto;
		}).toList();
	
		
		return ResponseEntity.ok(dtoList);

	}

	@DeleteMapping("/{id}")
	public void deleteBill(@PathVariable Long id) {
		billService.deleteBill(id);
	}

}
