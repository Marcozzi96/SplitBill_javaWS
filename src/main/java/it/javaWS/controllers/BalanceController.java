package it.javaWS.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import it.javaWS.services.BalanceService;

@RestController
@RequestMapping("/balance")
@SecurityRequirement(name = "bearerAuth")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    // GET /balance/3
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserBalance(@PathVariable Long userId) {
//        return balanceService.getDetailedBalance(userId);
        return ResponseEntity.ok(balanceService.getDetailedBalance(userId));
    }
}
