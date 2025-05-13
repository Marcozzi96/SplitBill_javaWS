package it.javaWS.javaws.controllers; 

import it.javaWS.javaws.dto.BillDTO;
import it.javaWS.javaws.dto.UserDTO;
import it.javaWS.javaws.models.Bill;
import it.javaWS.javaws.models.Group;
import it.javaWS.javaws.models.User;
import it.javaWS.javaws.models.UserGroup;
import it.javaWS.javaws.services.BillService;
import it.javaWS.javaws.services.GroupService;
import it.javaWS.javaws.services.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
public class BillController {

    private final BillService billService;
    private final UserService userService;
    private final GroupService groupService;

    public BillController(BillService billService, UserService userService, GroupService groupService) {
        this.billService = billService;
		this.userService = userService;
		this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<?> createBill(@RequestParam String description,
                           @RequestParam BigDecimal amount,
                           @RequestParam String notes,
                           @RequestParam Long buyerId,
                           @RequestParam Long groupId,
                           @RequestBody Map<Long, BigDecimal> usersDebit) {
    	
    	if(BigDecimal.ZERO.compareTo(amount) > 0) 
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "")); //amount è vuoto, 0 o negativo
    	Optional<User> buyerOpt = userService.getUser(buyerId);
    	
    	Group group = groupService.getGroup(groupId);

    	Set<UserGroup> userGroups = groupService.getUserGroup(groupId, usersDebit.keySet());
    	
    	//Set<UserGroup> userGroups = userGroupRepository.findByGroup_IdAndUser_IdIn(groupId, usersDebit.keySet());
    	if(userGroups.size() != usersDebit.size()) {
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Non tutti i debitori fanno parte del gruppo")); //NON TUTTI I CLIENTS FANNO PARTE DEL GRUPPO
    	}
    	Set<User> clients = userGroups.stream().map(ug->ug.getUser()).collect(Collectors.toSet());
        
    	if(buyerOpt.isEmpty() || group == null) 
    		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "buyerId o groupId non validi")); //buyerId o groupId non validi
    	
        User buyer = buyerOpt.get();
    	
    	Map<User,BigDecimal> usersDebitConvertito = new HashMap<User, BigDecimal>();
    	
    	for(User user : clients) {
    		usersDebitConvertito.put(user, usersDebit.get(user.getId()));
    	}
    	Bill bill = billService.createBill(description, amount, notes, buyer, group, usersDebitConvertito);
    	 
    		
    	BillDTO dto = new BillDTO(bill);
    	
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/group/{groupId}")
    public List<Bill> getBillsByGroup(@PathVariable Long groupId) {
        return billService.getBillsByGroup(groupId);
    }
    
    @DeleteMapping("/{id}")
    public void deleteBill(@PathVariable Long id) {
        billService.deleteBill(id);
    }

}
