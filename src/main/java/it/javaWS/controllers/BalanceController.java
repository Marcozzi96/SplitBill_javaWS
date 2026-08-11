package it.javaWS.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.UserBalanceDTO;
import it.javaWS.models.dto.UserSettlementDTO;
import it.javaWS.models.entities.User;
import it.javaWS.services.BalanceService;
import it.javaWS.utils.UnauthorizedAccessException;

@RestController
@RequestMapping("/balance")
@SecurityRequirement(name = "bearerAuth")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @Operation(summary = "Recupera il bilancio di un utente", description = "Ogni utente può consultare solo il proprio bilancio")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Bilancio restituito"),
            @ApiResponse(responseCode = "401", description = "Non autorizzato") })
    @GetMapping("/{userId}")
    public ResponseEntity<UserBalanceDTO> getUserBalance(@AuthenticationPrincipal User user, @PathVariable Long userId) {
        if (!user.getId().equals(userId)) {
            throw new UnauthorizedAccessException("Non sei autorizzato a consultare questo bilancio");
        }
        return ResponseEntity.ok(balanceService.getDetailedBalance(userId));
    }

    @Operation(summary = "Recupera il proprio bilancio globale", description = "Restituisce il saldo netto dell'utente autenticato")
    @ApiResponse(responseCode = "200", description = "Bilancio restituito")
    @GetMapping("/me")
    public ResponseEntity<UserBalanceDTO> getMyBalance(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(balanceService.getDetailedBalance(user.getId()));
    }

    @Operation(summary = "Recupera i propri settlement globali", description = "Restituisce l'elenco di chi deve a chi dal punto di vista dell'utente autenticato")
    @ApiResponse(responseCode = "200", description = "Settlement restituiti")
    @GetMapping("/settlements")
    public ResponseEntity<List<UserSettlementDTO>> getMySettlements(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(balanceService.getUserSettlements(user.getId()));
    }
}
