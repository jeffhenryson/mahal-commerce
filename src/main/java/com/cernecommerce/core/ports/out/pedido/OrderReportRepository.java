package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;

import java.time.Instant;
import java.util.List;

public interface OrderReportRepository {

    /**
     * Agrega os pedidos do período em um {@link OrderSummary}. {@code topProductsLimit} é o N do
     * top produtos por receita.
     */
    OrderSummary summarize(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int topProductsLimit);

    /**
     * Top N produtos do período, por receita ou por quantidade vendida
     * (GET /orders/analytics/top-products). {@code from}/{@code to} nunca chegam nulos aqui —
     * "histórico completo" é resolvido pelo chamador com os limites concretos, para não reabrir o
     * bug de bind nulo de {@code Instant} em JPQL já documentado em
     * {@code OrderRepositoryImpl#findAll}.
     */
    List<OrderSummary.TopProduct> findTopProducts(SalesChannel channel, OrderStatus status,
            Long customerId, Instant from, Instant to, int limit, boolean byQuantity);
}
