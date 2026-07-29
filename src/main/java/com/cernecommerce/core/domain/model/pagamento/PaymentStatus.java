package com.cernecommerce.core.domain.model.pagamento;

/**
 * Estado de um {@link OrderPayment} (PDV-F006).
 *
 * <p>O balcão só produz {@link #CAPTURED} — o dinheiro já está na gaveta no instante da venda, sem
 * passar por autorização. {@link #PENDING}/{@link #AUTHORIZED}/{@link #FAILED} existem para o
 * fluxo de gateway (Fatia 10), que confirma de forma assíncrona por webhook. {@link #REFUNDED} é o
 * estorno (PDV-F007, ainda não implementado).</p>
 */
public enum PaymentStatus {

    PENDING,
    AUTHORIZED,
    /** Dinheiro efetivamente recebido/confirmado. */
    CAPTURED,
    REFUNDED,
    FAILED
}
