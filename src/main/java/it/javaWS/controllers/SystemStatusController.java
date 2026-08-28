package it.javaWS.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.javaWS.models.dto.ServerStatusDTO;
import it.javaWS.services.SystemMetricsService;

/**
 * Metriche di sistema dell'host e del traffico HTTP.
 * Il path /api/status NON ricade nel pattern pubblico /status/** di SecurityConfig,
 * quindi richiede autenticazione JWT (regola anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/status")
public class SystemStatusController {

    private final SystemMetricsService systemMetricsService;

    public SystemStatusController(SystemMetricsService systemMetricsService) {
        this.systemMetricsService = systemMetricsService;
    }

    @GetMapping
    public ResponseEntity<ServerStatusDTO> getStatus() {
        return ResponseEntity.ok(systemMetricsService.getServerStatus());
    }
}
