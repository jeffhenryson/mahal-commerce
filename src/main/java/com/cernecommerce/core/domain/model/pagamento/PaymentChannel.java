package com.cernecommerce.core.domain.model.pagamento;

/**
 * Por onde o pagamento não-dinheiro foi cobrado no PDV (PDV-F025): a maquininha do balcão ou um link
 * de pagamento. Nulo em DINHEIRO e em todo pagamento anterior à V134.
 */
public enum PaymentChannel {
    MAQUININHA,
    LINK
}
