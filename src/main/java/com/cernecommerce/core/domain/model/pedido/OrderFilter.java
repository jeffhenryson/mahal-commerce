package com.cernecommerce.core.domain.model.pedido;

import java.time.Instant;

/**
 * Filtros da listagem de pedidos do administrador. Todos opcionais; {@code from}/{@code to} incidem
 * sobre {@code createdAt}, inclusivos.
 *
 * <p>PDV-F026 acrescentou {@code sessionId}, {@code comandaId} e {@code orderNumber}: a aba Caixas e o
 * detalhe da movimentação de estoque achavam o pedido por janela de tempo e filtravam no cliente.</p>
 */
public record OrderFilter(SalesChannel channel, OrderStatus status, Long customerId, Instant from, Instant to,
        Long sessionId, Long comandaId, String orderNumber) {

    public OrderFilter {
        orderNumber = orderNumber == null || orderNumber.isBlank() ? null : orderNumber.trim();
    }

    public static OrderFilter of(SalesChannel channel, OrderStatus status, Long customerId, Instant from,
            Instant to) {
        return new OrderFilter(channel, status, customerId, from, to, null, null, null);
    }
}
