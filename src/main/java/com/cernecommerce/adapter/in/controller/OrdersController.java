package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.OrderCancelRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderRefundRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderStatusRequest;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.OrderUseCase;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Visão de <b>pedidos do administrador</b> — atravessa canais.
 *
 * <p>Separada de {@code PdvController} porque o recorte é outro: o PDV enxerga a operação de um
 * caixa, esta superfície enxerga o pedido independentemente de ele ter nascido no balcão ou no
 * site. Aqui também aparecem <b>custo e margem</b>, que o DTO do PDV omite de propósito —
 * {@code PDV_READ} é a permissão mais distribuída do módulo.</p>
 */
@RestController
@RequestMapping("/orders")
@Tag(name = "Pedidos", description = "Visão do administrador, de todos os canais")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class OrdersController {

    private final OrderUseCase orderUseCase;
    private final OrderDTOConverter orderConverter;
    private final ApplicationEventPublisher publisher;

    public OrdersController(OrderUseCase orderUseCase, OrderDTOConverter orderConverter,
            ApplicationEventPublisher publisher) {
        this.orderUseCase = orderUseCase;
        this.orderConverter = orderConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Lista pedidos com filtros, do mais recente para o mais antigo",
            description = "Todo filtro é opcional. O período incide sobre a data de criação.")
    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<PageResult<OrderAdminResponseDTO>> listOrders(
            @RequestParam(required = false) SalesChannel channel,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(orderConverter.toAdminResponse(
                orderUseCase.listOrders(channel, status, customerId, from, to, page, size)));
    }

    @Operation(summary = "Detalha um pedido, com custo e margem por item")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public ResponseEntity<OrderAdminResponseDTO> getOrder(@PathVariable("id") Long orderId) {
        return ResponseEntity.ok(orderConverter.toAdminResponse(orderUseCase.getOrder(orderId)));
    }

    @Operation(summary = "Avança o pedido na esteira de fulfillment",
            description = "SEPARADO → ENVIADO → ENTREGUE. Pular etapas é recusado com 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida", content = @Content)
    })
    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    public ResponseEntity<OrderAdminResponseDTO> changeStatus(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderStatusRequest request, Authentication authentication) {
        Order before = orderUseCase.getOrder(orderId);
        Order order = orderUseCase.changeStatus(orderId, request.getStatus(), authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.ORDER_STATUS_CHANGED, authentication.getName(),
                Map.of("orderId", orderId,
                        "orderNumber", String.valueOf(order.orderNumber()),
                        "from", before.status().name(),
                        "to", order.status().name())));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }

    @Operation(summary = "Cancela um pedido ANTES de pagamento confirmado e libera a reserva de estoque",
            description = "Só pedidos sem pagamento confirmado (CRIADO/AGUARDANDO_PAGAMENTO). Pedido "
                    + "com pagamento confirmado usa /refund — cancelar e reembolsar são eventos "
                    + "diferentes (PDV-F007).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelado", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida: pagamento já confirmado (use /refund), ou já CANCELADO", content = @Content)
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public ResponseEntity<OrderAdminResponseDTO> cancelOrder(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderCancelRequest request, Authentication authentication) {
        Order before = orderUseCase.getOrder(orderId);
        Order order = orderUseCase.cancelOrder(orderId, request.getReason(), authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", orderId);
        details.put("orderNumber", String.valueOf(order.orderNumber()));
        details.put("statusBefore", before.status().name());
        details.put("reason", request.getReason());
        details.put("skus", order.items().stream().map(item -> item.sku()).toList());
        publisher.publishEvent(
                AuditEvent.of(EventType.ORDER_CANCELLED, authentication.getName(), details));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }

    @Operation(summary = "Reembolsa um pedido DEPOIS de pagamento confirmado",
            description = "Devolve a mercadoria ao estoque, estorna cada pagamento CAPTURED com uma "
                    + "linha REFUNDED do mesmo método e valor, e reverte no ledger de cashback todo "
                    + "ganho EARNED do pedido — tudo na mesma transação. Só pedidos com pagamento "
                    + "confirmado (PAGO em diante); pedido pré-pagamento usa /cancel.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reembolsado", content = @Content(schema = @Schema(implementation = OrderAdminResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida: sem pagamento confirmado (use /cancel), ou já CANCELADO/REEMBOLSADO", content = @Content)
    })
    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('ORDER_REFUND')")
    public ResponseEntity<OrderAdminResponseDTO> refundOrder(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderRefundRequest request, Authentication authentication) {
        Order before = orderUseCase.getOrder(orderId);
        Order order = orderUseCase.refundOrder(orderId, request.getReason(), authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", orderId);
        details.put("orderNumber", String.valueOf(order.orderNumber()));
        details.put("statusBefore", before.status().name());
        details.put("reason", request.getReason());
        details.put("skus", order.items().stream().map(item -> item.sku()).toList());
        publisher.publishEvent(
                AuditEvent.of(EventType.ORDER_REFUNDED, authentication.getName(), details));
        return ResponseEntity.ok(orderConverter.toAdminResponse(order));
    }
}
