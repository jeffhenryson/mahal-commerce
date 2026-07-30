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
     * Cancela um pedido ANTES de qualquer pagamento confirmado e <b>libera a reserva de
     * estoque</b> — nunca houve baixa real para devolver, só reserva (ou nem isso, no balcão, que
     * nunca reserva).
     *
     * <p>Cancelar e reembolsar são eventos diferentes (PDV-F007): contá-los juntos esconderia
     * quanto dinheiro de fato voltou ao cliente. Pedido com pagamento já confirmado usa
     * {@link #refundOrder}.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido já tiver pagamento confirmado (use {@link #refundOrder}) ou já estiver
     *         {@code CANCELADO}
     */
    Order cancelOrder(Long orderId, String reason, String username);

    /**
     * Reembolsa um pedido DEPOIS de pagamento confirmado, tudo na mesma transação: devolve a
     * mercadoria ao estoque (EST-F014 chegando pela porta do pedido), estorna cada pagamento
     * {@code CAPTURED} com uma linha {@code REFUNDED} do mesmo método e valor, e reverte no ledger
     * de cashback todo ganho {@code EARNED} do pedido.
     *
     * <p>Reembolsar um pedido já entregue é uma <b>devolução</b> — e devolução é entrada de
     * estoque legítima. O motivo do {@code StockMovement} carrega o número do pedido para a
     * trilha ser reconstruível.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido ainda não tiver pagamento confirmado (use {@link #cancelOrder}) ou já
     *         estiver {@code CANCELADO}/{@code REEMBOLSADO}
     */
    Order refundOrder(Long orderId, String reason, String username);
}
