package com.cernecommerce.adapter.out.payment;

import com.cernecommerce.core.domain.exception.pagamento.PaymentGatewayException;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter real do {@link PaymentGatewayPort} para o InfinitePay (ECM-F004, Fatia 10).
 *
 * <p>API do InfinitePay levantada em {@code infinitepay.io/checkout-documentacao} durante o
 * planejamento — não é a mesma forma de Mercado Pago que o plano original supunha (§2.6): aqui
 * o link de checkout é hospedado pelo próprio gateway (o cliente é redirecionado, não há QR code
 * embutido na resposta), e a criação do link <b>não</b> devolve um identificador de transação — só
 * o webhook revela {@code transaction_nsu}/{@code invoice_slug}. Por isso {@link
 * PaymentGatewayPort#createCheckoutLink} não devolve nada além da URL.</p>
 *
 * <p><b>Sem autenticação por chave de API</b> nos dois endpoints usados aqui — confirmado contra a
 * documentação pública do provider, não uma omissão. A correlação é só pelo {@code handle} (o
 * InfiniteTag da loja) e pelo {@code order_nsu} que nós mesmos definimos.</p>
 */
public class InfinitePayAdapter implements PaymentGatewayPort {

    private static final BigDecimal CENTS = BigDecimal.valueOf(100);

    private final RestClient restClient;
    private final String handle;
    private final String redirectUrl;
    private final String webhookUrl;

    public InfinitePayAdapter(RestClient restClient, String handle, String redirectUrl, String webhookUrl) {
        this.restClient = restClient;
        this.handle = handle;
        this.redirectUrl = redirectUrl;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public CheckoutLink createCheckoutLink(String orderNsu, BigDecimal amount, String itemDescription,
            String customerName, String customerEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("handle", handle);
        body.put("order_nsu", orderNsu);
        body.put("redirect_url", redirectUrl);
        body.put("webhook_url", webhookUrl);
        // Um item só, representando o líquido do pedido — os itens de linha do carrinho já foram
        // consolidados em Order.netAmount antes de chegar aqui; não há por que reabrir essa soma.
        body.put("items", List.of(Map.of(
                "quantity", 1,
                "price", toCents(amount),
                "description", itemDescription)));
        if (customerName != null || customerEmail != null) {
            body.put("customer", Map.of(
                    "name", customerName == null ? "" : customerName,
                    "email", customerEmail == null ? "" : customerEmail));
        }
        try {
            LinkResponse response = restClient.post()
                    .uri("/links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(LinkResponse.class);
            if (response == null || response.url() == null) {
                throw new IllegalStateException("resposta do InfinitePay sem url de checkout");
            }
            return new CheckoutLink(response.url());
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentGatewayException("Falha ao criar link de checkout InfinitePay", ex);
        }
    }

    @Override
    public PaymentCheckResult checkPayment(String orderNsu, String transactionNsu, String invoiceSlug) {
        Map<String, Object> body = Map.of(
                "handle", handle,
                "order_nsu", orderNsu,
                "transaction_nsu", transactionNsu,
                "slug", invoiceSlug);
        try {
            CheckResponse response = restClient.post()
                    .uri("/payment_check")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(CheckResponse.class);
            if (response == null) {
                return new PaymentCheckResult(false, null);
            }
            boolean paid = Boolean.TRUE.equals(response.paid());
            BigDecimal paidAmount = response.paidAmount() == null ? null : fromCents(response.paidAmount());
            return new PaymentCheckResult(paid, paidAmount);
        } catch (PaymentGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentGatewayException("Falha ao reconsultar pagamento InfinitePay", ex);
        }
    }

    private static long toCents(BigDecimal reais) {
        return reais.multiply(CENTS).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents).divide(CENTS, 2, RoundingMode.HALF_UP);
    }

    private record LinkResponse(String url) {
    }

    private record CheckResponse(
            @JsonProperty("success") Boolean success,
            @JsonProperty("paid") Boolean paid,
            @JsonProperty("amount") Long amount,
            @JsonProperty("paid_amount") Long paidAmount,
            @JsonProperty("installments") Integer installments,
            @JsonProperty("capture_method") String captureMethod) {
    }
}
