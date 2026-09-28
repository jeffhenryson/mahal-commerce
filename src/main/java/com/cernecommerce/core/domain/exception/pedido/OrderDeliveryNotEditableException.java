package com.cernecommerce.core.domain.exception.pedido;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;

/** Entrega de pedido cancelado ou reembolsado não é mais editável (PDV-F022). */
public class OrderDeliveryNotEditableException extends RuntimeException {

    public OrderDeliveryNotEditableException(Long orderId, OrderStatus status) {
        super("Entrega do pedido " + orderId + " não pode ser editada no status " + status);
    }
}
