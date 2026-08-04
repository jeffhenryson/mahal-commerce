package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderPaymentEntity;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OrderPaymentRepositoryImpl implements OrderPaymentRepository {

    private final OrderPaymentJpaRepository orderPaymentJpaRepository;

    public OrderPaymentRepositoryImpl(OrderPaymentJpaRepository orderPaymentJpaRepository) {
        this.orderPaymentJpaRepository = orderPaymentJpaRepository;
    }

    @Override
    public OrderPayment save(OrderPayment payment) {
        OrderPaymentEntity entity = new OrderPaymentEntity();
        entity.setId(payment.id());
        entity.setOrderId(payment.orderId());
        entity.setMethod(payment.method().name());
        entity.setAmount(payment.amount());
        entity.setStatus(payment.status().name());
        entity.setInstallments(payment.installments());
        entity.setGatewayRef(payment.gatewayRef());
        entity.setAuthorizedAt(payment.authorizedAt());
        entity.setCapturedAt(payment.capturedAt());
        entity.setCreatedAt(payment.createdAt());
        return toDomain(orderPaymentJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPayment> findByOrderId(Long orderId) {
        return orderPaymentJpaRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumCapturedAmountBySessionIdAndMethod(Long sessionId, PaymentMethod method) {
        BigDecimal sum = orderPaymentJpaRepository.sumCapturedAmountBySessionIdAndMethod(sessionId, method.name());
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPayment> findByGatewayRef(String gatewayRef) {
        return orderPaymentJpaRepository.findByGatewayRef(gatewayRef).map(this::toDomain);
    }

    private OrderPayment toDomain(OrderPaymentEntity e) {
        return OrderPayment.of(e.getId(), e.getOrderId(), PaymentMethod.valueOf(e.getMethod()), e.getAmount(),
                PaymentStatus.valueOf(e.getStatus()), e.getInstallments(), e.getGatewayRef(),
                e.getAuthorizedAt(), e.getCapturedAt(), e.getCreatedAt());
    }
}
