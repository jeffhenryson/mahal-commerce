package com.cernecommerce.core.domain.exception.pedido;

/** Pedido inexistente (PDV-F005). */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Pedido não encontrado: " + orderId);
    }
}
