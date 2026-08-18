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
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

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

    private final ComandaUseCase comandaUseCase;
    private final ComandaDTOConverter comandaConverter;
    private final OrderDTOConverter orderConverter;
    private final ApplicationEventPublisher publisher;

    public PdvComandaController(ComandaUseCase comandaUseCase, ComandaDTOConverter comandaConverter,
            OrderDTOConverter orderConverter, ApplicationEventPublisher publisher) {
        this.comandaUseCase = comandaUseCase;
        this.comandaConverter = comandaConverter;
        this.orderConverter = orderConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Abre uma comanda na sessão do operador autenticado")
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
                authentication.getName());
        return ResponseEntity.created(URI.create("/pdv/comandas/" + comanda.id()))
                .body(comandaConverter.toResponse(comanda));
    }

    @Operation(summary = "Lança um item na comanda aberta, debitando o estoque na hora")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado", content = @Content(schema = @Schema(implementation = ComandaResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Saldo insuficiente", content = @Content),
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda ou SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, ou produto sem preço", content = @Content)
    })
    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('PDV_COMANDA_MANAGE')")
    public ResponseEntity<ComandaResponseDTO> addItem(@PathVariable("id") Long comandaId,
            @Valid @RequestBody AddComandaItemRequest request, Authentication authentication) {
        Comanda comanda = comandaUseCase.addItem(comandaId, request.getSku(), request.getQuantity(),
                authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "PDV_COMANDA_ITEM",
                        "comandaId", comandaId,
                        "warehouseCode", comanda.warehouseCode(),
                        "type", MovementType.SAIDA.name(),
                        "sku", request.getSku())));
        return ResponseEntity.status(201).body(comandaConverter.toResponse(comanda));
    }

    @Operation(summary = "Consulta uma comanda, com o total corrente")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<ComandaResponseDTO> getComanda(@PathVariable("id") Long comandaId) {
        return ResponseEntity.ok(comandaConverter.toResponse(comandaUseCase.getComanda(comandaId)));
    }

    @Operation(summary = "Lista as comandas abertas de uma sessão — as \"mesas ocupadas\"")
    @GetMapping
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<ComandaResponseDTO>> listOpenComandas(@RequestParam Long sessionId) {
        return ResponseEntity.ok(comandaConverter.toResponse(comandaUseCase.listOpenComandas(sessionId)));
    }

    @Operation(summary = "Fecha a comanda, convertendo os itens acumulados num pedido concluído",
            description = "Mesmo contrato de pagamento de POST /pdv/sessions/{id}/sales: payments "
                    + "exige pelo menos uma linha, e só DINHEIRO pode ser tendido a mais para gerar "
                    + "troco. O estoque já foi debitado item a item em cada lançamento — o "
                    + "fechamento não toca em saldo de novo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fechada", content = @Content(schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Pagamento insuficiente", content = @Content),
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
            @ApiResponse(responseCode = "404", description = "Comanda não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "Comanda não está aberta, sem itens, ou pagamento não-dinheiro acima do total", content = @Content)
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
            @ApiResponse(responseCode = "403", description = "A sessão é de outro operador", content = @Content),
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
