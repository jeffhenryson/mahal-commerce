package com.cernecommerce.adapter.in.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Corpo da notificação do InfinitePay (ECM-F004) — só os três campos que
 * {@code PaymentWebhookUseCase} realmente usa. Os demais campos do payload real ({@code amount},
 * {@code paid_amount}, {@code capture_method}, {@code items}...) são deliberadamente ignorados: o
 * valor pago nunca é lido daqui, só reconsultado no gateway.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentWebhookRequest {

    @JsonProperty("order_nsu")
    private String orderNsu;

    @JsonProperty("transaction_nsu")
    private String transactionNsu;

    @JsonProperty("invoice_slug")
    private String invoiceSlug;
}
