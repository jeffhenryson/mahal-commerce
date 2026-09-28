package com.cernecommerce.core.domain.exception.pedido;

/** Edição de entrega num pedido que não foi vendido com entrega nem retirada (PDV-F022). */
public class OrderHasNoDeliveryException extends RuntimeException {

    public OrderHasNoDeliveryException(Long orderId) {
        super("Pedido " + orderId + " não tem entrega registrada");
    }
}
