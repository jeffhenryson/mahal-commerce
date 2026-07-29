package com.cernecommerce.core.domain.model.pagamento;

/**
 * Forma de pagamento de um {@link OrderPayment} (PDV-F006).
 *
 * <p>Só os quatro métodos do balcão físico existem por ora. {@code GATEWAY_PIX}/{@code
 * GATEWAY_CARTAO} da Fatia 10 entram quando o gateway (Mercado Pago ou outro, ainda não decidido)
 * for integrado — adicionar uma constante nova não quebra nada gravado hoje.</p>
 */
public enum PaymentMethod {

    /** Único método em que pode haver troco. */
    DINHEIRO,
    DEBITO,
    /** Único método que aceita {@link OrderPayment#installments()}. */
    CREDITO,
    PIX
}
