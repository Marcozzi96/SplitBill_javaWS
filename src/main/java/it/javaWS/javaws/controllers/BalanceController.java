package it.javaWS.javaws.controllers;

import it.javaWS.javaws.dto.UserBalanceDTO;
import it.javaWS.javaws.services.BalanceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/balance")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    // GET /balance/3
    @GetMapping("/{userId}")
    public UserBalanceDTO getUserBalance(@PathVariable Long userId) {
        return balanceService.getDetailedBalance(userId);
    }
}
