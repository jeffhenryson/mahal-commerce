package com.cernecommerce.core.domain.model.pedido;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado de um {@link Order} (PDV-F003).
 *
 * <p>Uma máquina de estados só, com dois caminhos. O do balcão é degenerado de propósito:
 * {@code CRIADO → CONCLUIDO} acontece na mesma transação, porque no balcão a mercadoria sai e o
 * dinheiro entra no mesmo instante. Duas máquinas separadas seriam mais complexas que esta, não
 * menos.</p>
 *
 * <p>As transições vivem aqui, e não espalhadas pelo service, para que a resposta a "este pedido
 * pode ir para aquele estado?" tenha um lugar só — e um teste só.</p>
 */
public enum OrderStatus {

    /** Pedido de balcão recém-montado. Estado efêmero: vira {@link #CONCLUIDO} na mesma transação. */
    CRIADO,

    /** Pedido de marketplace com estoque <b>reservado</b>, esperando a confirmação do pagamento. */
    AGUARDANDO_PAGAMENTO,

    /** Pagamento confirmado. A reserva virou saída de estoque de verdade. */
    PAGO,

    /** Separado para envio ou retirada. */
    SEPARADO,

    /** A caminho do cliente. */
    ENVIADO,

    /** Entregue. Terminal. */
    ENTREGUE,

    /** Venda de balcão finalizada. Terminal, exceto por devolução. */
    CONCLUIDO,

    /** Cancelado, com os estornos correspondentes já aplicados. Terminal. */
    CANCELADO;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            CRIADO, EnumSet.of(CONCLUIDO, CANCELADO),
            AGUARDANDO_PAGAMENTO, EnumSet.of(PAGO, CANCELADO),
            PAGO, EnumSet.of(SEPARADO, CANCELADO),
            SEPARADO, EnumSet.of(ENVIADO, CANCELADO),
            ENVIADO, EnumSet.of(ENTREGUE, CANCELADO),
            ENTREGUE, EnumSet.of(CANCELADO),
            CONCLUIDO, EnumSet.of(CANCELADO),
            CANCELADO, EnumSet.noneOf(OrderStatus.class));

    /**
     * Indica se este estado é final — nenhuma transição parte dele.
     *
     * <p>Só {@link #CANCELADO} é terminal de verdade. {@link #ENTREGUE} e {@link #CONCLUIDO} ainda
     * aceitam cancelamento, porque devolução existe e precisa de um caminho.</p>
     */
    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }

    /** Indica se a transição {@code this → target} é permitida. */
    public boolean canTransitionTo(OrderStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }

    /** Estados alcançáveis a partir deste. */
    public Set<OrderStatus> allowedTransitions() {
        return ALLOWED.get(this);
    }
}
