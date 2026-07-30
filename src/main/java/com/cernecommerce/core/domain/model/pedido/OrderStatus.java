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

    /**
     * Finalizada no balcão: mercadoria entregue e dinheiro recebido. Vale tanto para a venda de
     * balcão quanto para o pedido do app retirado e pago na loja. Terminal, exceto por reembolso.
     */
    CONCLUIDO,

    /**
     * Cancelado ANTES de qualquer pagamento confirmado — nunca houve dinheiro capturado nem baixa
     * real de estoque para desfazer, só reserva (ou nem isso, no balcão). Terminal.
     */
    CANCELADO,

    /**
     * Reembolsado DEPOIS de pagamento confirmado — com estorno de pagamento, devolução de estoque
     * e reversão do cashback ganho já aplicados (PDV-F007). Terminal.
     *
     * <p>Distinto de {@link #CANCELADO} de propósito: cancelar e reembolsar são eventos diferentes,
     * e contá-los juntos esconderia quanto dinheiro de fato voltou ao cliente.</p>
     */
    REEMBOLSADO;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            CRIADO, EnumSet.of(CONCLUIDO, CANCELADO),
            // CONCLUIDO aqui é a retirada no balcão: o cliente montou o pedido no app, veio à loja,
            // pagou no caixa e levou a mercadoria. Terminou exatamente como uma venda de balcão
            // termina — inventar um caminho de "retirada" pela esteira de envio faria o pedido
            // passar por SEPARADO e ENVIADO para uma entrega que aconteceu no balcão.
            AGUARDANDO_PAGAMENTO, EnumSet.of(PAGO, CONCLUIDO, CANCELADO),
            // A partir daqui pagamento já foi confirmado: só REEMBOLSADO faz sentido como saída
            // antecipada, nunca CANCELADO — sempre há dinheiro e estoque real para reverter.
            PAGO, EnumSet.of(SEPARADO, REEMBOLSADO),
            SEPARADO, EnumSet.of(ENVIADO, REEMBOLSADO),
            ENVIADO, EnumSet.of(ENTREGUE, REEMBOLSADO),
            ENTREGUE, EnumSet.of(REEMBOLSADO),
            CONCLUIDO, EnumSet.of(REEMBOLSADO),
            CANCELADO, EnumSet.noneOf(OrderStatus.class),
            REEMBOLSADO, EnumSet.noneOf(OrderStatus.class));

    /**
     * Indica se este estado é final — nenhuma transição parte dele.
     *
     * <p>{@link #CANCELADO} e {@link #REEMBOLSADO} são os dois terminais — um por evento
     * (cancelamento pré-pagamento ou reembolso pós-pagamento), nunca os dois para o mesmo pedido.</p>
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
