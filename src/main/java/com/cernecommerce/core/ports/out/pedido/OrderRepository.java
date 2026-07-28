package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Port de saída para persistência de pedidos, de qualquer canal (PDV-F005).
 *
 * <p>Substitui o {@code SaleRepository} anterior, que expunha <b>só</b> {@code save()} — a venda
 * era write-only, e não havia como relê-la pela API. Isso precedia qualquer campo faltando no
 * modelo.</p>
 */
public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    /** Pedidos de uma sessão de caixa, do mais recente para o mais antigo. */
    PageResult<Order> findBySessionId(Long sessionId, int page, int size);

    /**
     * Listagem filtrada para a visão do administrador, do mais recente para o mais antigo.
     * Qualquer filtro {@code null} é ignorado.
     *
     * @param from início do período, sobre {@code createdAt}, inclusivo
     * @param to fim do período, sobre {@code createdAt}, inclusivo
     */
    PageResult<Order> findAll(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size);

    /**
     * Soma do líquido dos pedidos <b>concluídos</b> da sessão — a receita que a sessão gerou.
     * Pedidos cancelados não entram.
     *
     * <p><b>Aproximação temporária:</b> soma <i>todas</i> as formas de pagamento, porque
     * {@code order_payment} só existe na Fatia 3. A conferência da gaveta deveria considerar apenas
     * o que entrou em <b>dinheiro</b>; enquanto isso não existe, o esperado incluirá cartão e PIX e
     * vai divergir do físico assim que a loja aceitar a primeira venda não-dinheiro. Está
     * documentado no README do módulo.</p>
     *
     * @return zero quando não houve venda — nunca {@code null}
     */
    BigDecimal sumConcludedNetAmountBySessionId(Long sessionId);

    /**
     * Próximo número de pedido, de sequência dedicada.
     *
     * <p>Não deriva do id: {@code BIGSERIAL} deixa buracos quando uma transação faz rollback, e
     * buraco em numeração de documento fiscal é problema com o fisco. Consumido na
     * <b>conclusão</b> do pedido, não na criação.</p>
     */
    String nextOrderNumber();
}
