package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.SaleItemRequest;
import com.cernecommerce.adapter.in.dtos.response.OrderItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;

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
