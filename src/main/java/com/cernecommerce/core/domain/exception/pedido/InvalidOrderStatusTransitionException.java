package com.cernecommerce.core.domain.exception.pedido;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;

/**
 * Transição de estado que a máquina do pedido não permite (PDV-F003).
 *
 * <p>Cobre tanto o erro de operação — cancelar duas vezes, faturar pedido já entregue — quanto o
 * de programação. A mensagem lista os destinos válidos porque o chamador quase sempre precisa
 * saber o que <i>poderia</i> ter feito.</p>
 */
public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(Long orderId, OrderStatus from, OrderStatus to) {
        super("Transição inválida no pedido " + orderId + ": " + from + " → " + to
                + (from.isTerminal()
                        ? ". " + from + " é estado terminal."
                        : ". A partir de " + from + " só é possível ir para " + from.allowedTransitions() + "."));
    }
}
