package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderPaymentJpaRepository extends JpaRepository<OrderPaymentEntity, Long> {

    List<OrderPaymentEntity> findByOrderIdOrderByIdAsc(Long orderId);

    /** Único por {@code uk_order_payment_gateway_ref} (V68) — derived query é segura aqui. */
    Optional<OrderPaymentEntity> findByGatewayRef(String gatewayRef);

    /**
     * Join implícito com {@code OrderEntity} pelo par {@code order_id = id} — não há
     * {@code @ManyToOne} entre as duas entidades (ver o javadoc de {@code OrderPaymentEntity}),
     * então o vínculo é feito na própria query, no molde de {@code StockIntegrityJpaRepository}.
     *
     * <p>{@code COALESCE} porque uma sessão sem pagamento nenhum daquele método é o caso normal
     * (ex.: caixa que só recebeu em dinheiro nunca gera linha de PIX), e devolver {@code null}
     * obrigaria todo chamador a tratá-lo.</p>
     */
    @Query("""
            SELECT COALESCE(SUM(p.amount), 0)
            FROM OrderPaymentEntity p, OrderEntity o
            WHERE p.orderId = o.id
              AND o.sessionId = :sessionId
              AND p.status = 'CAPTURED'
              AND p.method = :method
            """)
    BigDecimal sumCapturedAmountBySessionIdAndMethod(@Param("sessionId") Long sessionId,
            @Param("method") String method);
}
