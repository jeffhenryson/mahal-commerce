package com.cernecommerce.core.ports.out.pagamento;

import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de saída para o ledger de pagamentos de pedido (PDV-F006).
 *
 * <p>Append-only por desenho: não há {@code update}. Corrigir um pagamento errado é uma linha nova
 * em sentido contrário (estorno, PDV-F007), não uma edição — a mesma regra do resto do módulo.</p>
 */
public interface OrderPaymentRepository {

    OrderPayment save(OrderPayment payment);

    /** Pagamentos de um pedido, na ordem em que foram lançados. */
    List<OrderPayment> findByOrderId(Long orderId);

    /**
     * Soma dos pagamentos {@code CAPTURED} de um método, entre os pedidos liquidados por uma
     * sessão de caixa — é o que dá o total "recebido em dinheiro", "recebido em débito" etc. no
     * fechamento. Zero quando não houve nenhum, nunca {@code null}.
     */
    BigDecimal sumCapturedAmountBySessionIdAndMethod(Long sessionId, PaymentMethod method);
}
