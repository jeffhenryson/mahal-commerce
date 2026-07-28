package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderEntity;
import com.cernecommerce.adapter.out.persistence.entity.OrderItemEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class OrderRepositoryImpl implements OrderRepository {

    /** Largura da numeração de pedido, zero-padded — {@code 000001000}. */
    private static final int ORDER_NUMBER_WIDTH = 9;

    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderEntity entity = orderJpaRepository.findById(order.id() == null ? -1L : order.id())
                .orElseGet(OrderEntity::new);
        entity.setOrderNumber(order.orderNumber());
        entity.setChannel(order.channel().name());
        entity.setStatus(order.status().name());
        entity.setCustomerId(order.customerId());
        entity.setSessionId(order.sessionId());
        entity.setWarehouseCode(order.warehouseCode());
        entity.setTotalAmount(order.grossAmount());
        entity.setDiscountAmount(order.discountAmount());
        entity.setCashbackRedeemed(order.cashbackRedeemed());
        entity.setNetAmount(order.netAmount());
        entity.setChangeAmount(order.changeAmount());
        entity.setCancelReason(order.cancelReason());
        entity.setCreatedAt(order.createdAt());
        entity.setPaidAt(order.paidAt());
        entity.setConcludedAt(order.concludedAt());
        entity.setCancelledAt(order.cancelledAt());

        // Os itens são reescritos por inteiro: o pedido é imutável depois de concluído, então este
        // caminho só é exercitado antes da conclusão. orphanRemoval limpa os antigos.
        entity.getItems().clear();
        for (OrderItem item : order.items()) {
            OrderItemEntity itemEntity = new OrderItemEntity();
            itemEntity.setOrder(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setCostPrice(item.costPrice());
            itemEntity.setDiscountAmount(item.discountAmount());
            itemEntity.setCashbackPercent(item.cashbackPercent());
            entity.getItems().add(itemEntity);
        }
        return toDomain(orderJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findBySessionId(Long sessionId, int page, int size) {
        Page<OrderEntity> result = orderJpaRepository
                .findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> findAll(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size) {
        Page<OrderEntity> result = orderJpaRepository.findFiltered(
                channel == null ? null : channel.name(),
                status == null ? null : status.name(),
                customerId, from, to, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumConcludedNetAmountBySessionId(Long sessionId) {
        BigDecimal sum = orderJpaRepository.sumConcludedNetAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public String nextOrderNumber() {
        Long next = orderJpaRepository.nextOrderNumber();
        return String.format("%0" + ORDER_NUMBER_WIDTH + "d", next);
    }

    private Order toDomain(OrderEntity e) {
        return Order.of(e.getId(), e.getOrderNumber(), SalesChannel.valueOf(e.getChannel()),
                OrderStatus.valueOf(e.getStatus()), e.getCustomerId(), e.getSessionId(),
                e.getWarehouseCode(), e.getItems().stream().map(this::toDomain).toList(),
                e.getTotalAmount(), e.getDiscountAmount(), e.getCashbackRedeemed(), e.getNetAmount(),
                e.getChangeAmount(), e.getCancelReason(), e.getCreatedAt(), e.getPaidAt(),
                e.getConcludedAt(), e.getCancelledAt(), e.getVersion() == null ? 0L : e.getVersion());
    }

    private OrderItem toDomain(OrderItemEntity e) {
        return OrderItem.of(e.getId(), e.getSku(), e.getQuantity(), e.getUnitPrice(), e.getCostPrice(),
                e.getDiscountAmount(), e.getCashbackPercent());
    }
}
