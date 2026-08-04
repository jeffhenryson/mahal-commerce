package com.cernecommerce.core.ports.out.ecommerce;

import java.math.BigDecimal;

/**
 * Port de saída para o gateway de pagamento do marketplace (ECM-F004, Fatia 10) — hoje
 * implementado pelo InfinitePay.
 *
 * <p>Deliberadamente sem um enum de status "genérico de gateway": o InfinitePay só distingue
 * pago/não-pago em {@code payment_check} (ver {@link PaymentCheckResult}), então não há um
 * vocabulário próprio do provider para normalizar. Um gateway futuro com estados intermediários
 * (autorizado, em análise) pediria revisão desta interface, não uma tradução forçada aqui.</p>
 */
public interface PaymentGatewayPort {

    /**
     * Cria o link de checkout hospedado pelo gateway — o cliente é redirecionado para lá, não há
     * QR code embutido devolvido por esta chamada.
     *
     * @param orderNsu identificador que CORRELACIONA a cobrança ao nosso pedido — sempre
     *        {@code Order.id()} como string. O gateway não devolve uma referência própria aqui
     *        (só na notificação); é este valor que fecha o vínculo.
     */
    CheckoutLink createCheckoutLink(String orderNsu, BigDecimal amount, String itemDescription,
            String customerName, String customerEmail);

    /**
     * Reconsulta o gateway pela verdade sobre uma cobrança — nunca se decide a partir do corpo do
     * webhook, sempre daqui. {@code transactionNsu}/{@code invoiceSlug} vêm do webhook e são
     * usados só como chave de busca, nunca como fonte do valor pago.
     */
    PaymentCheckResult checkPayment(String orderNsu, String transactionNsu, String invoiceSlug);

    record CheckoutLink(String checkoutUrl) {
    }

    /** {@code paidAmount} é o que o GATEWAY confirma — é ele, não o do webhook, que vale. */
    record PaymentCheckResult(boolean paid, BigDecimal paidAmount) {
    }
}
