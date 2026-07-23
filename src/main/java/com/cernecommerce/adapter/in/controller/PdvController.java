package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.SaleDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.SaleRequest;
import com.cernecommerce.adapter.in.dtos.response.SaleResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Sale;
import com.cernecommerce.core.domain.model.pdv.SaleItem;
import com.cernecommerce.core.ports.in.PdvUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controller do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Fluxo hexagonal Controller → {@link PdvUseCase} → service. Endpoints previstos
 * (TODO): abertura/sangria/fechamento de caixa.</p>
 */
@RestController
@RequestMapping("/pdv")
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvController {

    private final PdvUseCase pdvUseCase;
    private final SaleDTOConverter saleConverter;

    public PdvController(PdvUseCase pdvUseCase, SaleDTOConverter saleConverter) {
        this.pdvUseCase = pdvUseCase;
        this.saleConverter = saleConverter;
    }

    @Operation(summary = "Lista sessões de caixa")
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<CashRegisterSession>> listSessions(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(pdvUseCase.listSessions(page, size));
    }

    @Operation(summary = "Registra uma venda de balcão na sessão de caixa e dá baixa automática no estoque")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = SaleResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente para algum item", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão de caixa não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão de caixa encerrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/sessions/{id}/sales")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<SaleResponseDTO> registerSale(@PathVariable("id") Long sessionId,
            @Valid @RequestBody SaleRequest request, Authentication authentication) {
        List<SaleItem> items = saleConverter.toItems(request.getItems());
        Sale sale = pdvUseCase.registerSale(sessionId, request.getWarehouseCode(), items, authentication.getName());
        return ResponseEntity.status(201).body(saleConverter.toResponse(sale));
    }
}
