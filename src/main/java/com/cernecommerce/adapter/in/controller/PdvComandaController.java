package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.AddComandaItemRequest;
import com.cernecommerce.adapter.in.dtos.request.CloseComandaRequest;
import com.cernecommerce.adapter.in.dtos.request.OpenComandaRequest;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.pdv.CourtesyNotAllowedException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller de <b>comanda de mesa</b> (PDV-F009), separado de {@link PdvController}: o PDV
 * atual modela venda pontual de balcão, e a comanda é um pedido incremental de horas — o caso do
 * lounge de narguilé. Endpoints novos, sem mudar {@code POST /pdv/sessions/{id}/sales}.
 */
@RestController
@RequestMapping("/pdv/comandas")
@Tag(name = "PDV (Comanda de Mesa)", description = "Pedidos incrementais numa sessão de caixa aberta por horas")
@SecurityRequirement(name = "bearerAuth")
public class PdvComandaController {

    /**
     * PDV-F010 — cortesia é desconto de 100%, e desconto tem dono. Checada aqui, e não no service,
     * pelo mesmo motivo de {@code PDV_SALE_DISCOUNT} em {@code PdvController}: o núcleo não conhece
     * Spring Security.
     */
    private static final String COURTESY_AUTHORITY = "PDV_COMANDA_COURTESY";

    private final ComandaUseCase comandaUseCase;
    private final ComandaDTOConverter comandaConverter;
    private final OrderDTOConverter orderConverter;
    private final CrmUseCase crmUseCase;
    private final ApplicationEventPublisher publisher;

    public PdvComandaController(ComandaUseCase comandaUseCase, ComandaDTOConverter comandaConverter,
            OrderDTOConverter orderConverter, CrmUseCase crmUseCase, ApplicationEventPublisher publisher) {
        this.comandaUseCase = comandaUseCase;
        this.comandaConverter = comandaConverter;
        this.orderConverter = orderConverter;
        this.crmUseCase = crmUseCase;
        this.publisher = publisher;
    }

    /**
     * Resolve {@code customerName} no CRM para as comandas já convertidas — em lote, para a lista
     * de mesas abertas não pagar uma consulta por mesa. Mesmo padrão de
     * {@code OrdersController.enrichCustomerNames}.
     */
    private List<ComandaResponseDTO> enrichCustomerNames(List<ComandaResponseDTO> comandas) {
        List<Long> customerIds = comandas.stream()
                .map(ComandaResponseDTO::getCustomerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (customerIds.isEmpty()) {
            return comandas;
        }
        Map<Long, String> names = crmUseCase.findCustomerNames(customerIds);
        comandas.forEach(dto -> dto.setCustomerName(names.get(dto.getCustomerId())));
        return comandas;
    }

    /** Ver {@link #COURTESY_AUTHORITY}. {@code TROCA} é cortesia mesmo sem o cliente marcar. */
    private void requireCourtesyAuthority(Boolean courtesy, ConsumptionMode mode,
            Authentication authentication) {
        boolean isCourtesy = Boolean.TRUE.equals(courtesy)
                || (mode != null && mode.impliesCourtesy());
        if (!isCourtesy) {
            return;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(COURTESY_AUTHORITY::equals);
        if (!allowed) {
            throw new CourtesyNotAllowedException(authentication.getName());
        }
    }

    @Operation(summary = "Abre uma comanda na sessão do operador autenticado",
            description = "Abrir exige a PRÓPRIA sessão: a mesa nasce na gaveta de quem a abriu, e "
                    + "é esse depósito que baixa estoque. Operar mesa já aberta (lançar, fechar, "
                    + "cancelar) é que não exige posse — ver PDV-F010.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aberta", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Sessão encerrada", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> openComanda(@RequestParam Long sessionId,
            @Valid @RequestBody OpenComandaRequest request, Authentication authentication) {
        Comanda comanda = comandaUseCase.openComanda(sessionId, request.getTableOrCustomerLabel(),
                request.getCustomerId(), authentication.getName());
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.created(URI.create("/pdv/comandas/" + comanda.id())).body(dto);
    }

    @Operation(summary = "Lança um item na comanda aberta, debitando o estoque na hora",
            description = "Aceita item comum e linha de sessão de narguilé (PDV-F010). O preço "
                    + "unitário é sempre resolvido pelo servidor: NORMAL e SABOR_EXTRA cobram o "
                    + "preço da variação do sabor, OPEN_ROSH cobra o openRoshPrice do produto PAI "
                    + "(não o da variação), e cortesia/TROCA gravam zero — sempre com o custo "
                    + "congelado normalmente, para a margem mostrar o prejuízo real da promo. "
                    + "Cortesia exige PDV_COMANDA_COURTESY. A mesa pode ser operada por quem não é "
                    + "dono do caixa que a abriu.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente, SKU sem disponibilidade para mesa (NOT_AVAILABLE_FOR_TABLE), modo de sessão em SKU comum (NOT_A_SESSION_PRODUCT), open rosh sem preço (OPEN_ROSH_NOT_PRICED) ou linha de origem ausente (LINKED_ITEM_REQUIRED)", content = @Content),
            @ApiResponse(responseCode = "403", description = "Cortesia sem PDV_COMANDA_COURTESY (COURTESY_NOT_ALLOWED)", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda ou SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, produto sem preço, sessão de caixa encerrada, ou troca sobre linha que não é open rosh (NOT_AN_OPEN_ROSH)", content = @Content)
    })
    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addItem(@PathVariable("id") Long comandaId,
            @Valid @RequestBody AddComandaItemRequest request, Authentication authentication) {
        requireCourtesyAuthority(request.getCourtesy(), request.getMode(), authentication);
        Comanda comanda = comandaUseCase.addItem(comandaId, request.getSku(), request.getQuantity(),
                request.getMode(), Boolean.TRUE.equals(request.getCourtesy()), request.getLinkedItemId(),
                authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "PDV_COMANDA_ITEM",
                        "comandaId", comandaId,
                        "warehouseCode", comanda.warehouseCode(),
                        "type", MovementType.SAIDA.name(),
                        "sku", request.getSku(),
                        // A cortesia baixa estoque sem entrar dinheiro: é exatamente o lançamento
                        // que alguém vai querer auditar depois de um fechamento estranho.
                        "mode", request.getMode() == null ? ConsumptionMode.NORMAL.name() : request.getMode().name(),
                        "courtesy", Boolean.TRUE.equals(request.getCourtesy()))));
        ComandaResponseDTO dto = comandaConverter.toResponse(comanda);
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.status(201).body(dto);
    }

    @Operation(summary = "Consulta uma comanda, com o total corrente")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<ComandaResponseDTO> getComanda(@PathVariable("id") Long comandaId) {
        ComandaResponseDTO dto = comandaConverter.toResponse(comandaUseCase.getComanda(comandaId));
        enrichCustomerNames(List.of(dto));
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Lista as comandas abertas de uma sessão — as \"mesas ocupadas\"")
    @GetMapping
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<ComandaResponseDTO>> listOpenComandas(@RequestParam Long sessionId) {
        return ResponseEntity.ok(
                enrichCustomerNames(comandaConverter.toResponse(comandaUseCase.listOpenComandas(sessionId))));
    }

    @Operation(summary = "Fecha a comanda, convertendo os itens acumulados num pedido concluído",
            description = "Mesmo contrato de pagamento de POST /pdv/sessions/{id}/sales: payments "
                    + "exige pelo menos uma linha, e só DINHEIRO pode ser tendido a mais para gerar "
                    + "troco. O estoque já foi debitado item a item em cada lançamento — o "
                    + "fechamento não toca em saldo de novo. O pedido gerado NASCE com channel = "
                    + "MESA (o canal é imutável), carregando comandaId, tableLabel e o cliente da "
                    + "mesa. Qualquer atendente com PDV_COMANDA_MANAGE pode fechar, e o pedido "
                    + "entra na sessão de caixa de QUEM FECHA — o dinheiro pertence à gaveta que o "
                    + "recebeu.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechada", content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Pagamento insuficiente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, sem itens (COMANDA_EMPTY), só com cortesias (COMANDA_ONLY_COURTESY), quem fecha não tem caixa aberto, ou pagamento não-dinheiro acima do total", content = @Content)
    })
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<OrderResponseDTO> closeComanda(@PathVariable("id") Long comandaId,
            @Valid @RequestBody CloseComandaRequest request, Authentication authentication) {
        List<PaymentCommand> payments = request.getPayments().stream()
                .map(p -> new PaymentCommand(
                        com.cernecommerce.core.domain.model.pagamento.PaymentMethod.valueOf(p.getMethod()),
                        p.getAmount(), p.getInstallments()))
                .toList();
        Order order = comandaUseCase.closeComanda(comandaId, payments, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "PDV_COMANDA_CLOSE",
                        "comandaId", comandaId,
                        "orderNumber", order.orderNumber(),
                        "warehouseCode", order.warehouseCode())));
        if (order.totalCashbackEarned().signum() > 0) {
            publisher.publishEvent(AuditEvent.of(EventType.CASHBACK_EARNED, authentication.getName(),
                    Map.of("orderId", order.id(), "orderNumber", order.orderNumber(),
                            "amount", order.totalCashbackEarned())));
        }
        return ResponseEntity.ok(orderConverter.toResponse(order));
    }

    @Operation(summary = "Abandona a comanda sem cobrança, devolvendo ao estoque cada item já lançado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelada", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta", content = @Content)
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> cancelComanda(@PathVariable("id") Long comandaId,
            Authentication authentication) {
        Comanda comanda = comandaUseCase.cancelComanda(comandaId, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "PDV_COMANDA_CANCEL",
                        "comandaId", comandaId,
                        "warehouseCode", comanda.warehouseCode(),
                        "type", MovementType.ENTRADA.name())));
        return ResponseEntity.ok(comandaConverter.toResponse(comanda));
    }
}
