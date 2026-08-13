package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, Long> {

    /**
     * Top N produtos por receita no período (GET /orders/summary). {@code LEFT JOIN} por
     * <b>valor</b> contra {@code product.sku} — não há FK entre {@code order_item} e
     * {@code product} (o item congela o sku no instante da venda; o produto pode ter sido
     * renomeado ou excluído depois). SKU órfão aparece com o próprio código como nome, nunca é
     * descartado: a venda aconteceu, e omiti-la subestimaria o produto mais vendido do período.
     * Mesma restrição de "pagamento confirmado, exceto reembolsado" das outras agregações de
     * receita — ver {@link OrderJpaRepository#findRevenueTotals}.
     */
    @Query("""
            SELECT oi.sku AS sku, COALESCE(p.name, oi.sku) AS productName,
                   SUM(oi.quantity) AS quantitySold,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount), 0) AS revenue
            FROM OrderItemEntity oi
            JOIN oi.order o
            LEFT JOIN ProductEntity p ON p.sku = oi.sku
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO')
            GROUP BY oi.sku, p.name
            ORDER BY revenue DESC
            """)
    List<TopProductProjection> findTopProducts(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * Igual a {@link #findTopProducts}, ordenado por quantidade vendida em vez de receita
     * ({@code GET /orders/analytics/top-products?sortBy=quantity}).
     */
    @Query("""
            SELECT oi.sku AS sku, COALESCE(p.name, oi.sku) AS productName,
                   SUM(oi.quantity) AS quantitySold,
                   COALESCE(SUM(oi.quantity * oi.unitPrice - oi.discountAmount), 0) AS revenue
            FROM OrderItemEntity oi
            JOIN oi.order o
            LEFT JOIN ProductEntity p ON p.sku = oi.sku
            WHERE (:channel    IS NULL OR o.channel    = :channel)
              AND (:status     IS NULL OR o.status     = :status)
              AND (:customerId IS NULL OR o.customerId = :customerId)
              AND o.createdAt >= :from
              AND o.createdAt <= :to
              AND o.status IN ('PAGO','SEPARADO','ENVIADO','ENTREGUE','CONCLUIDO')
            GROUP BY oi.sku, p.name
            ORDER BY quantitySold DESC
            """)
    List<TopProductProjection> findTopProductsByQuantity(@Param("channel") String channel,
            @Param("status") String status, @Param("customerId") Long customerId,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    interface TopProductProjection {
        String getSku();
        String getProductName();
        BigDecimal getQuantitySold();
        BigDecimal getRevenue();
    }
}
