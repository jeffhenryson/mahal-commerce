package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.SaleItemRequest;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;

import java.math.BigDecimal;
import java.util.List;

public class OrderDTOConverter {

    /**
     * Converte os itens do request em comandos de venda. Note que <b>não há preço</b> aqui: o
     * request só informa SKU, quantidade e desconto (PDV-F004).
     */
    public List<SaleItemCommand> toCommands(List<SaleItemRequest> requests) {
        return requests.stream()
                .map(r -> new SaleItemCommand(r.getSku(), r.getQuantity(), r.getDiscountAmount()))
                .toList();
    }

    public OrderResponseDTO toResponse(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setChannel(order.channel().name());
        dto.setStatus(order.status().name());
        dto.setCustomerId(order.customerId());
        dto.setSessionId(order.sessionId());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setNetAmount(order.netAmount());
        dto.setChangeAmount(order.changeAmount());
        dto.setCancelReason(order.cancelReason());
        dto.setCreatedAt(order.createdAt());
        dto.setPaidAt(order.paidAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setCancelledAt(order.cancelledAt());
        dto.setItems(order.items().stream().map(this::toResponse).toList());
        return dto;
    }

    public PageResult<OrderResponseDTO> toResponse(PageResult<Order> page) {
        return new PageResult<>(page.content().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    // ── Visão do administrador: acrescenta custo e margem ────────────────────────────────────

    public OrderAdminResponseDTO toAdminResponse(Order order) {
        OrderAdminResponseDTO dto = new OrderAdminResponseDTO();
        dto.setId(order.id());
        dto.setOrderNumber(order.orderNumber());
        dto.setChannel(order.channel().name());
        dto.setStatus(order.status().name());
        dto.setCustomerId(order.customerId());
        dto.setSessionId(order.sessionId());
        dto.setWarehouseCode(order.warehouseCode());
        dto.setGrossAmount(order.grossAmount());
        dto.setDiscountAmount(order.discountAmount());
        dto.setCashbackRedeemed(order.cashbackRedeemed());
        dto.setNetAmount(order.netAmount());
        dto.setChangeAmount(order.changeAmount());
        dto.setMarginAmount(totalMargin(order));
        dto.setCancelReason(order.cancelReason());
        dto.setCreatedAt(order.createdAt());
        dto.setPaidAt(order.paidAt());
        dto.setConcludedAt(order.concludedAt());
        dto.setCancelledAt(order.cancelledAt());
        dto.setAllowedTransitions(order.status().allowedTransitions().stream()
                .map(Enum::name).sorted().toList());
        dto.setItems(order.items().stream().map(this::toAdminResponse).toList());
        return dto;
    }

    public PageResult<OrderAdminResponseDTO> toAdminResponse(PageResult<Order> page) {
        return new PageResult<>(page.content().stream().map(this::toAdminResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private OrderItemAdminResponseDTO toAdminResponse(OrderItem item) {
        OrderItemAdminResponseDTO dto = new OrderItemAdminResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setGrossAmount(item.grossAmount());
        dto.setNetAmount(item.netAmount());
        dto.setCostPrice(item.costPrice());
        dto.setMarginAmount(item.marginAmount());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setCashbackAmount(item.cashbackAmount());
        return dto;
    }

    /**
     * Margem total do pedido. Devolve {@code null} — e não um total parcial — se <b>algum</b> item
     * não tiver custo congelado: somar só os itens conhecidos produziria um número que parece a
     * margem do pedido e não é, o que é pior do que não ter número nenhum.
     */
    private BigDecimal totalMargin(Order order) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.items()) {
            BigDecimal margin = item.marginAmount();
            if (margin == null) {
                return null;
            }
            total = total.add(margin);
        }
        return total;
    }

    private OrderItemResponseDTO toResponse(OrderItem item) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setDiscountAmount(item.discountAmount());
        dto.setGrossAmount(item.grossAmount());
        dto.setNetAmount(item.netAmount());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setCashbackAmount(item.cashbackAmount());
        return dto;
    }
}
