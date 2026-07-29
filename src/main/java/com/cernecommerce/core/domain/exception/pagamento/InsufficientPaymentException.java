package com.cernecommerce.core.domain.exception.pagamento;

import java.math.BigDecimal;

/** Soma dos pagamentos informados não cobre o líquido do pedido (PDV-F006). */
public class InsufficientPaymentException extends RuntimeException {

    public InsufficientPaymentException(BigDecimal totalPaid, BigDecimal netAmount) {
        super("Pagamento insuficiente: recebido " + totalPaid + ", devido " + netAmount);
    }
}
