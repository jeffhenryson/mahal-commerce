package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.PdvUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller stub do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Esqueleto: demonstra o fluxo hexagonal Controller → {@link PdvUseCase} →
 * service. Endpoints previstos (TODO): abertura/sangria/fechamento de caixa e
 * itens de venda balcão. Endpoint atual requer {@code PDV_READ}.</p>
 */
@RestController
@RequestMapping("/pdv")
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvController {

    private final PdvUseCase pdvUseCase;

    public PdvController(PdvUseCase pdvUseCase) {
        this.pdvUseCase = pdvUseCase;
    }

    @Operation(summary = "Lista sessões de caixa (stub — retorna página vazia)")
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<CashRegisterSession>> listSessions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(pdvUseCase.listSessions(page, size));
    }
}
