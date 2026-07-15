package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.logistica.Shipment;
import com.cernecommerce.core.ports.in.LogisticaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller stub do domínio <b>logistica</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link LogisticaUseCase} → service.
 * Endpoints previstos (TODO): expedição/status, clique e retire, rotas de motoboy,
 * transportadora. Autorização por permissão RBAC a definir.</p>
 */
@RestController
@RequestMapping("/logistica")
@Tag(name = "Logística", description = "Expedição, clique e retire e rotas — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
public class LogisticaController {

    private final LogisticaUseCase logisticaUseCase;

    public LogisticaController(LogisticaUseCase logisticaUseCase) {
        this.logisticaUseCase = logisticaUseCase;
    }

    // TODO: @PreAuthorize com permissão RBAC do domínio logistica.
    @Operation(summary = "Lista expedições (stub — retorna vazio)")
    @GetMapping("/shipments")
    public ResponseEntity<List<Shipment>> listShipments() {
        return ResponseEntity.ok(logisticaUseCase.listShipments());
    }
}
