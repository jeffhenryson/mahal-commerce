package com.cernecommerce.core.domain.model.crm;

/**
 * Status de uma entrada do log de disparo de campanha. {@link #PENDENTE_INTEGRACAO} é usado quando
 * a automação não tem {@code webhookUrl} configurado (comportamento legado, sem envio real).
 * {@link #ENVIADO}/{@link #FALHA} refletem o resultado real da chamada ao webhook (ver
 * crm/webhook-disparo-real, F008).
 */
public enum CampaignDispatchStatus {
    PENDENTE_INTEGRACAO,
    ENVIADO,
    FALHA
}
