package com.cernecommerce.core.domain.model.crm;

/**
 * Status de uma entrada do log de disparo de campanha. {@link #PENDENTE_INTEGRACAO} é o único
 * valor possível hoje — não existe canal de envio real conectado (ver crm/integracao-canal-envio,
 * F008). Modelado como enum para permitir novos status (ex.: ENVIADO, FALHOU) sem quebrar o
 * contrato de API quando a integração existir.
 */
public enum CampaignDispatchStatus {
    PENDENTE_INTEGRACAO
}
