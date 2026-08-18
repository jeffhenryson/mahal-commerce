package com.cernecommerce.core.domain.model.crm;

/**
 * Resultado de uma chamada ao webhook de uma automação. Nunca representa uma exceção não
 * capturada — {@code CampaignWebhookPort.send} sempre devolve uma instância, mesmo em caso de
 * timeout ou erro de rede, para que o disparo em lote não seja interrompido por um único
 * cliente-alvo com webhook indisponível.
 */
public record WebhookDispatchResult(boolean success, Integer statusCode, String errorMessage) {

    public static WebhookDispatchResult ok(int statusCode) {
        return new WebhookDispatchResult(true, statusCode, null);
    }

    public static WebhookDispatchResult failure(String errorMessage) {
        return new WebhookDispatchResult(false, null, errorMessage);
    }

    public static WebhookDispatchResult failure(int statusCode, String errorMessage) {
        return new WebhookDispatchResult(false, statusCode, errorMessage);
    }
}
