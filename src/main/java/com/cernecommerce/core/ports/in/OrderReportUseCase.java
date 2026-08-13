package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;

import java.time.Instant;
import java.util.List;

/**
 * Port de entrada do resumo agregado de vendas (GET /orders/summary).
 *
 * <p>Separado de {@link OrderUseCase} de propósito: agregação de relatório é só leitura, sem
 * efeito colateral no ciclo de vida do pedido, ortogonal às dependências que {@code OrderService}
 * já carrega (estoque, pagamento, cashback).</p>
 */
public interface OrderReportUseCase {

    /**
     * Resumo agregado de vendas do período. {@code from}/{@code to} são obrigatórios — agregação
     * sem limite de período é a consulta mais cara do domínio.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException se
     *         {@code from} for depois de {@code to}, ou o intervalo exceder o teto permitido
     */
    OrderSummary getSummary(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to);

    /**
     * Top N produtos por receita ou quantidade vendida (GET /orders/analytics/top-products).
     * Diferente de {@link #getSummary}, {@code from}/{@code to} são opcionais: nulos significam
     * histórico completo (usado para "best seller da loja"), sem o teto de período que
     * {@code getSummary} aplica — pedir o histórico inteiro é o próprio caso de uso, então não há
     * o que limitar.
     */
    List<OrderSummary.TopProduct> getTopProducts(SalesChannel channel, OrderStatus status,
            Long customerId, Instant from, Instant to, int limit, boolean byQuantity);
}
