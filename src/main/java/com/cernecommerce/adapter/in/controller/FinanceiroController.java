package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller stub do domínio <b>financeiro</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link FinanceiroUseCase} → service.
 * Endpoints previstos (TODO): DRE simplificado, fluxo de caixa, conciliação de taxas
 * de gateway. Autorização por permissão RBAC a definir.</p>
 */
@RestController
@RequestMapping("/financeiro")
@Tag(name = "Financeiro", description = "DRE, fluxo de caixa e conciliação — esqueleto, implementação pendente")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class FinanceiroController {

    private final FinanceiroUseCase financeiroUseCase;

    public FinanceiroController(FinanceiroUseCase financeiroUseCase) {
        this.financeiroUseCase = financeiroUseCase;
    }

    // TODO: @PreAuthorize com permissão RBAC do domínio financeiro.
    @Operation(summary = "Lista lançamentos de fluxo de caixa (stub — retorna página vazia)")
    @GetMapping("/cash-flow")
    public ResponseEntity<PageResult<CashFlowEntry>> listCashFlow(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(financeiroUseCase.listCashFlow(page, size));
    }
}
