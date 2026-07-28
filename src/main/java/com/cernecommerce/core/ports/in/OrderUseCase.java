package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;

import java.time.Instant;

/**
 * Port de entrada da visão de <b>pedidos do administrador</b> — atravessa canais.
 *
 * <p>Fica fora de {@code PdvUseCase} de propósito: o PDV enxerga a operação de um caixa, enquanto
 * esta superfície enxerga o pedido independentemente de ter nascido no balcão ou no site. Misturar
 * as duas faria o PDV crescer para caber o marketplace.</p>
 */
public interface OrderUseCase {

    /** Listagem filtrada, do mais recente para o mais antigo. Filtro {@code null} é ignorado. */
    PageResult<Order> listOrders(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size);

    /**
     * Busca um pedido pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se não existir
     */
    Order getOrder(Long orderId);

    /**
     * Avança o pedido na esteira de fulfillment.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se a transição não for permitida pela máquina de estados
     */
    Order changeStatus(Long orderId, OrderStatus newStatus, String username);

    /**
     * Cancela o pedido e <b>devolve a mercadoria ao estoque</b>, tudo na mesma transação
     * (EST-F014 chegando pela porta do pedido).
     *
     * <p>Cancelar um pedido já entregue é uma <b>devolução</b> — e devolução é entrada de estoque
     * legítima. O motivo do {@code StockMovement} carrega o número do pedido para a trilha ser
     * reconstruível.</p>
     *
     * <p>O estorno do <b>pagamento</b> depende da Fatia 3 e o {@code REVERSED} do <b>cashback</b>
     * depende da Fatia 4 — nenhum dos dois acontece aqui ainda.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido já estiver cancelado
     */
    Order cancelOrder(Long orderId, String reason, String username);
}
