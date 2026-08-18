package com.cernecommerce.adapter.out.webhook;

import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.util.Map;

/**
 * Dispara o POST real de webhook das automações do CRM (n8n/Make). Nunca lança exceção — captura
 * timeout, erro de rede e status HTTP não-2xx internamente, para que um cliente-alvo com webhook
 * indisponível não interrompa o disparo em lote (ver {@code CrmService.dispatchAutomation}).
 */
class CampaignWebhookAdapter implements CampaignWebhookPort {

    private static final Logger log = LoggerFactory.getLogger(CampaignWebhookAdapter.class);
    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final RestClient restClient;

    CampaignWebhookAdapter(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public WebhookDispatchResult send(String url, Map<String, String> headers, Object payload) {
        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(URI.create(url))
                    .headers(h -> {
                        if (headers != null) {
                            headers.forEach(h::add);
                        }
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return WebhookDispatchResult.ok(response.getStatusCode().value());
        } catch (RestClientResponseException ex) {
            log.warn("crm.webhook.dispatch.failed url={} status={}", url, ex.getStatusCode().value());
            return WebhookDispatchResult.failure(ex.getStatusCode().value(), truncate(ex.getMessage()));
        } catch (Exception ex) {
            log.warn("crm.webhook.dispatch.failed url={} error={}", url, ex.toString());
            return WebhookDispatchResult.failure(truncate(ex.getMessage()));
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MESSAGE_MAX_LENGTH ? message.substring(0, ERROR_MESSAGE_MAX_LENGTH) : message;
    }
}
