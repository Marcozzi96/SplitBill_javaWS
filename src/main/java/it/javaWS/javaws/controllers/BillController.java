package it.javaWS.javaws.controllers; 

import it.javaWS.javaws.models.Bill;
import it.javaWS.javaws.services.BillService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/bills")
public class BillController {

    private final BillService billService;

    public BillController(BillService billService) {
        this.billService = billService;
    }

    @PostMapping
    public Bill createBill(@RequestParam String description,
                           @RequestParam BigDecimal amount,
                           @RequestParam String notes,
                           @RequestParam Long buyerId,
                           @RequestParam Long groupId) {
        return billService.createBill(description, amount, notes, buyerId, groupId);
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
