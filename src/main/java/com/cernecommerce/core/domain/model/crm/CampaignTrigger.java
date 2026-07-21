package com.cernecommerce.core.domain.model.crm;

/**
 * Gatilho de uma automação de campanha. Apenas {@link #MANUAL} é disparado nesta versão
 * (via {@code POST /crm/automacoes/{id}/disparar}) — os demais valores são metadados
 * descritivos para uma futura engine de disparo automático.
 */
public enum CampaignTrigger {
    MANUAL,
    ENTRADA_ESTAGIO
}
