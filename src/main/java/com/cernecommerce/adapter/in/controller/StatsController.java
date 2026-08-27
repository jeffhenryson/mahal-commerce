package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.response.StatsResponseDTO;
import com.cernecommerce.core.domain.model.StatsResult;
import com.cernecommerce.core.ports.in.StatsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@SecurityRequirement(name = "bearerAuth")
public class StatsController {

    private final StatsUseCase statsUseCase;

    public StatsController(StatsUseCase statsUseCase) {
        this.statsUseCase = statsUseCase;
    }

    @Operation(summary = "Totais do dashboard — substitui 4 requests separados")
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ') and hasAuthority('ROLE_READ')")
    public ResponseEntity<StatsResponseDTO> stats() {
        StatsResult r = statsUseCase.getStats();
        return ResponseEntity.ok(new StatsResponseDTO(
                r.totalUsers(), r.activeUsers(), r.disabledUsers(),
                r.totalRoles(), r.totalPermissions()));
    }
}
