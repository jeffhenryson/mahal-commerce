package com.cernecommerce.core.domain.model.pedido;

/** Como a mercadoria de uma venda chega ao cliente (PDV-F022). */
public enum DeliveryType {
    /** O cliente busca na loja depois — a venda nasce {@link OrderStatus#RESERVADO}. */
    RETIRADA,
    /** A loja leva até o endereço — a venda nasce {@link OrderStatus#RESERVADO} e segue a esteira de expedição. */
    ENTREGA
}
