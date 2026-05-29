package com.securityspring.adapter.in.controller;

import com.securityspring.adapter.in.dtos.response.AuditLogResponseDTO;
import com.securityspring.core.domain.model.AuditLogEntry;
import com.securityspring.core.domain.model.PageResult;
import com.securityspring.core.ports.in.AuditLogsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit-logs")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class AuditLogController {

    private final AuditLogsUseCase useCase;

    public AuditLogController(AuditLogsUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "Lista histórico de auditoria paginado")
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<PageResult<AuditLogResponseDTO>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<AuditLogEntry> result = useCase.list(userId, action, page, Math.min(size, 100));
        PageResult<AuditLogResponseDTO> response = new PageResult<>(
                result.content().stream().map(AuditLogResponseDTO::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }
}
