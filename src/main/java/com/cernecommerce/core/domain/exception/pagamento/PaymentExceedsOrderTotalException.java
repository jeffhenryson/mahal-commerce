package com.cernecommerce.core.domain.exception.pagamento;

import java.math.BigDecimal;

/**
 * A soma dos pagamentos que <b>não</b> são {@code DINHEIRO} passa do líquido do pedido (PDV-F006).
 *
 * <p>Só dinheiro pode ser "tendido a mais" e gerar troco — débito, crédito e PIX são lançados pelo
 * valor exato que o operador decide cobrar naquele meio. Deixar um meio não-dinheiro sozinho
 * exceder o total seria ou erro de digitação ou uma forma de sacar dinheiro da gaveta disfarçada
 * de troco, e nenhum dos dois casos deve virar {@code Order.changeAmount} silenciosamente.</p>
 */
public class PaymentExceedsOrderTotalException extends RuntimeException {

    public PaymentExceedsOrderTotalException(BigDecimal nonCashTotal, BigDecimal netAmount) {
        super("Pagamento em débito/crédito/PIX (" + nonCashTotal + ") não pode passar do total do "
                + "pedido (" + netAmount + "); só dinheiro pode ser tendido a mais para gerar troco");
    }
}
