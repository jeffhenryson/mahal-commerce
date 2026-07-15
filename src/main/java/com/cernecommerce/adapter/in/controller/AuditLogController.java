package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.response.AuditLogResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.ports.in.AuditLogsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/audit-logs")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class AuditLogController {

    private final AuditLogsUseCase useCase;

    public AuditLogController(AuditLogsUseCase useCase) {
        this.useCase = useCase;
    }

    private static final Set<String> DEV_ONLY_EVENTS = Set.of("DEV_ELEVATION_COMPLETED");

    @Operation(summary = "Lista histórico de auditoria paginado com filtros opcionais")
    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<PageResult<AuditLogResponseDTO>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @Parameter(description = "Início do intervalo (ISO-8601, ex: 2026-05-01T00:00:00Z)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Fim do intervalo (ISO-8601, ex: 2026-05-31T23:59:59Z)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        Set<String> excludeActions = AuthUtils.isDevAccess(auth) ? Set.of() : DEV_ONLY_EVENTS;
        PageResult<AuditLogEntry> result = useCase.list(username, action, from, to, page, Math.min(size, 100), excludeActions);
        PageResult<AuditLogResponseDTO> response = new PageResult<>(
                result.content().stream().map(AuditLogResponseDTO::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lista todos os tipos de evento de auditoria válidos para uso no filtro ?action=")
    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    public ResponseEntity<List<String>> listActions() {
        List<String> actions = Arrays.stream(EventType.values())
                .map(Enum::name)
                .sorted()
                .toList();
        return ResponseEntity.ok(actions);
    }
}
