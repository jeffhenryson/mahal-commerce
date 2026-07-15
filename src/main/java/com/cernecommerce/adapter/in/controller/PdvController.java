package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.PdvUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller stub do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Esqueleto: demonstra o fluxo hexagonal Controller → {@link PdvUseCase} →
 * service. Endpoints previstos (TODO): abertura/sangria/fechamento de caixa e
 * itens de venda balcão. Autorização por permissão RBAC a definir.</p>
 */
@RestController
@RequestMapping("/pdv")
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
public class PdvController {

    private final PdvUseCase pdvUseCase;

    public PdvController(PdvUseCase pdvUseCase) {
        this.pdvUseCase = pdvUseCase;
    }

    // TODO: @PreAuthorize com permissão RBAC do domínio PDV.
    @Operation(summary = "Lista sessões de caixa (stub — retorna vazio)")
    @GetMapping("/sessions")
    public ResponseEntity<List<CashRegisterSession>> listSessions() {
        return ResponseEntity.ok(pdvUseCase.listSessions());
    }
}
