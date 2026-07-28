package com.cernecommerce.core.ports.out.pedido;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;

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
     * Próximo número de pedido, de sequência dedicada.
     *
     * <p>Não deriva do id: {@code BIGSERIAL} deixa buracos quando uma transação faz rollback, e
     * buraco em numeração de documento fiscal é problema com o fisco. Consumido na
     * <b>conclusão</b> do pedido, não na criação.</p>
     */
    String nextOrderNumber();
}
