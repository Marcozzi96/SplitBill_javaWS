package it.javaWS.controllers;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.PaymentDTO;
import it.javaWS.models.entities.User;
import it.javaWS.services.PaymentService;

@RestController
@RequestMapping("/payments")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Operation(summary = "Registra un rimborso", description = "Registra un rimborso tra due utenti. L'importo non può superare il debito effettivo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rimborso registrato"),
            @ApiResponse(responseCode = "400", description = "Dati non validi"),
            @ApiResponse(responseCode = "401", description = "Non autorizzato"),
            @ApiResponse(responseCode = "409", description = "Il rimborso supera il debito effettivo") })
    @PostMapping
    public ResponseEntity<PaymentDTO> createPayment(@AuthenticationPrincipal User user,
            @RequestParam Long payeeId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(paymentService.createPayment(user.getId(), payeeId, amount, groupId, notes, user.getId()));
    }

    @Operation(summary = "Recupera i propri rimborsi", description = "Restituisce la cronologia dei rimborsi in cui l'utente è coinvolto, con paginazione")
    @ApiResponse(responseCode = "200", description = "Cronologia rimborsi restituita")
    @GetMapping
    public ResponseEntity<Page<PaymentDTO>> getPayments(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(paymentService.getPaymentsForUser(user.getId(), pageable));
    }
}
