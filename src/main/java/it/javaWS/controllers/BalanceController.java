package it.javaWS.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.models.dto.UserBalanceDTO;
import it.javaWS.services.BalanceService;

@RestController
@RequestMapping("/balance")
@SecurityRequirement(name = "bearerAuth")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserBalanceDTO> getUserBalance(@PathVariable Long userId) {
        return ResponseEntity.ok(balanceService.getDetailedBalance(userId));
    }
}
