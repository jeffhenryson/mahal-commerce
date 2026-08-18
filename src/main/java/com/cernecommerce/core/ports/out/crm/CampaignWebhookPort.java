package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;

import java.util.Map;

/**
 * Port de saída para o disparo real de webhook das automações do CRM. Implementações nunca lançam
 * exceção — sempre devolvem um {@link WebhookDispatchResult}, mesmo em caso de timeout ou erro de
 * rede, para que um cliente-alvo com webhook indisponível não interrompa o disparo em lote.
 */
public interface CampaignWebhookPort {

    WebhookDispatchResult send(String url, Map<String, String> headers, Object payload);
}
