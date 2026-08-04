package com.cernecommerce.core.domain.exception.pagamento;

/**
 * Falha ao criar cobrança ou reconsultar pagamento no gateway externo (ECM-F004) — rede, timeout
 * ou erro do provider. No checkout, desfaz o pedido e a reserva de estoque (mesma transação). No
 * webhook, sinaliza ao controller que a notificação deve ser tentada de novo.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
