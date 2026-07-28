package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public class OrderService implements OrderUseCase {

    private final OrderRepository orderRepository;
    private final EstoqueUseCase estoqueUseCase;

    public OrderService(OrderRepository orderRepository, EstoqueUseCase estoqueUseCase) {
        this.orderRepository = orderRepository;
        this.estoqueUseCase = estoqueUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listOrders(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size) {
        return orderRepository.findAll(channel, status, customerId, from, to, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional
    public Order changeStatus(Long orderId, OrderStatus newStatus, String username) {
        // A validação da transição mora em OrderStatus/Order e já tem teste — aqui só se orquestra.
        return orderRepository.save(getOrder(orderId).withStatus(newStatus));
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId, String reason, String username) {
        Order order = getOrder(orderId);

        // cancelled() valida a transição e recusa cancelar duas vezes. Chamá-lo ANTES de mexer no
        // estoque é o que impede um segundo cancelamento de devolver a mercadoria de novo.
        Order cancelled = order.cancelled(reason, Instant.now());

        // Devolução é entrada de estoque legítima, inclusive para pedido já entregue. O motivo
        // carrega o número do pedido: sem ele, a trilha do movimento não é reconstruível.
        String movementReason = "Cancelamento do pedido " + order.orderNumber();
        for (OrderItem item : order.items()) {
            estoqueUseCase.adjustStock(item.sku(), order.warehouseCode(), MovementType.ENTRADA,
                    item.quantity(), movementReason, username);
        }
        return orderRepository.save(cancelled);
    }
}
