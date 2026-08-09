package it.javaWS.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.entities.Bill;
import it.javaWS.models.entities.Transaction;
import it.javaWS.models.entities.User;
import it.javaWS.services.BillService;
import it.javaWS.services.GroupService;
import it.javaWS.services.TransactionService;
import it.javaWS.utils.UnauthorizedAccessException;

@RestController
@RequestMapping("/transactions")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final BillService billService;
    private final GroupService groupService;

    public TransactionController(TransactionService transactionService, BillService billService, GroupService groupService) {
        this.transactionService = transactionService;
        this.billService = billService;
        this.groupService = groupService;
    }

    @Operation(summary = "Elimina una transazione", description = "Consente l'eliminazione solo al buyer della spesa correlata o all'admin del gruppo")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transazione eliminata"),
            @ApiResponse(responseCode = "401", description = "Non autorizzato"),
            @ApiResponse(responseCode = "404", description = "Transazione non trovata") })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Transaction transaction = transactionService.getTransaction(id);
        Bill bill = billService.getBill(transaction.getBill().getId());
        boolean isBuyer = bill.getBuyer().getId().equals(user.getId());
        boolean isAdmin = groupService.isUserAdminOfGroup(bill.getGroup().getId(), user.getId());
        if (!isBuyer && !isAdmin) {
            throw new UnauthorizedAccessException("Non sei autorizzato a eliminare questa transazione");
        }
        transactionService.deleteTransaction(id);
        return ResponseEntity.ok().build();
    }


}
