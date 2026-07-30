package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.CashbackDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CashbackRatePatchRequest;
import com.cernecommerce.adapter.in.dtos.request.CashbackRateRequest;
import com.cernecommerce.adapter.in.dtos.response.CashbackBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackEntryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackMarginImpactResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackRateResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Programa de cashback (CRM-F003): taxas por abrangência, extrato do cliente e diagnóstico de
 * margem. Resgate no balcão e ajuste manual ficam para uma fatia seguinte.
 */
@RestController
@RequestMapping("/cashback")
@Tag(name = "Cashback", description = "Programa de fidelidade: taxas, ledger e diagnóstico de margem")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class CashbackController {

    private final CashbackUseCase cashbackUseCase;
    private final CashbackDTOConverter converter;
    private final ApplicationEventPublisher publisher;

    public CashbackController(CashbackUseCase cashbackUseCase, CashbackDTOConverter converter,
            ApplicationEventPublisher publisher) {
        this.cashbackUseCase = cashbackUseCase;
        this.converter = converter;
        this.publisher = publisher;
    }

    @Operation(summary = "Cria uma taxa de cashback (GLOBAL, CATEGORY ou SKU)",
            description = "scopeRef é obrigatório para CATEGORY/SKU e deve vir ausente para GLOBAL. "
                    + "validFrom omitido vale a partir de agora; validTo omitido é vigência em aberto.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada", content = @Content(schema = @Schema(implementation = CashbackRateResponseDTO.class))),
            @ApiResponse(responseCode = "409", description = "Já existe taxa ativa para a mesma abrangência", content = @Content),
            @ApiResponse(responseCode = "400", description = "Requisição inválida", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/rates")
    @PreAuthorize("hasAuthority('CASHBACK_RATE_MANAGE')")
    public ResponseEntity<CashbackRateResponseDTO> createRate(@Valid @RequestBody CashbackRateRequest request,
            Authentication authentication) {
        CashbackRate created = cashbackUseCase.createRate(converter.toScope(request.getScope()),
                request.getScopeRef(), request.getPercent(), request.getValidFrom(), request.getValidTo());
        publisher.publishEvent(AuditEvent.of(EventType.CASHBACK_RATE_CHANGED, authentication.getName(),
                Map.of("rateId", created.id(), "scope", created.scope().name())));
        return ResponseEntity.created(URI.create("/cashback/rates/" + created.id()))
                .body(converter.toResponse(created));
    }

    @Operation(summary = "Lista taxas de cashback paginadas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/rates")
    @PreAuthorize("hasAuthority('CASHBACK_READ')")
    public ResponseEntity<PageResult<CashbackRateResponseDTO>> listRates(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<CashbackRate> result = cashbackUseCase.listRates(page, size);
        return ResponseEntity.ok(new PageResult<>(result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @Operation(summary = "Altera parcialmente uma taxa de cashback",
            description = "Campo ausente ou nulo é mantido. Não altera a abrangência (scope/scopeRef).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Atualizada", content = @Content(schema = @Schema(implementation = CashbackRateResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Taxa não encontrada", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/rates/{id}")
    @PreAuthorize("hasAuthority('CASHBACK_RATE_MANAGE')")
    public ResponseEntity<CashbackRateResponseDTO> patchRate(@PathVariable Long id,
            @Valid @RequestBody CashbackRatePatchRequest request, Authentication authentication) {
        CashbackRate updated = cashbackUseCase.patchRate(id, request.getPercent(), request.getActive(),
                request.getValidTo());
        publisher.publishEvent(AuditEvent.of(EventType.CASHBACK_RATE_CHANGED, authentication.getName(),
                Map.of("rateId", updated.id(), "scope", updated.scope().name())));
        return ResponseEntity.ok(converter.toResponse(updated));
    }

    @Operation(summary = "Resolve a taxa vigente para um SKU",
            description = "Cadeia SKU → CATEGORY → GLOBAL — devolve a regra ativa mais específica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK — body vazio se nenhuma taxa se aplica"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado no catálogo", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/rates/resolve")
    @PreAuthorize("hasAuthority('CASHBACK_READ')")
    public ResponseEntity<CashbackRateResponseDTO> resolveRate(
            @RequestParam @NotBlank @Size(min = 3, max = 50) String sku) {
        CashbackRate resolved = cashbackUseCase.resolveApplicableRate(sku);
        return resolved == null ? ResponseEntity.ok().build() : ResponseEntity.ok(converter.toResponse(resolved));
    }

    @Operation(summary = "Produtos cuja taxa vigente consome mais de X% da margem do item",
            description = "Diagnóstico que evita descobrir um item de baixa margem com taxa alta só "
                    + "no fechamento do mês. Usa Pricing.marginPercent(), sem matemática nova.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "maxShare ausente ou fora de faixa", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/margin-impact")
    @PreAuthorize("hasAuthority('CASHBACK_READ')")
    public ResponseEntity<List<CashbackMarginImpactResponseDTO>> getMarginImpact(
            @RequestParam @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal maxShare) {
        return ResponseEntity.ok(cashbackUseCase.findMarginImpact(maxShare).stream()
                .map(converter::toResponse).toList());
    }

    @Operation(summary = "Saldo de cashback do cliente: disponível, pendente e a expirar em breve")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CashbackBalanceResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CASHBACK_READ')")
    public ResponseEntity<CashbackBalanceResponseDTO> getCustomerBalance(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(cashbackUseCase.getCustomerBalance(id)));
    }

    @Operation(summary = "Extrato de cashback do cliente, paginado, mais recente primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @GetMapping("/customers/{id}/entries")
    @PreAuthorize("hasAuthority('CASHBACK_READ')")
    public ResponseEntity<PageResult<CashbackEntryResponseDTO>> listCustomerEntries(@PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageResult<CashbackEntry> result = cashbackUseCase.listCustomerEntries(id, page, size);
        return ResponseEntity.ok(new PageResult<>(result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages()));
    }
}
