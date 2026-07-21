package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Entrada do log de disparo de uma automação de campanha para um cliente-alvo.
 * {@code convertidoEm} fica sempre {@code null} nesta versão — rastrear conversão depende do
 * domínio de pedidos, que ainda não existe no backend (ver crm/listagem-clientes-rfm).
 */
public record CampaignLogEntry(
    Long id,
    Long automationId,
    Long customerId,
    CampaignDispatchStatus status,
    Instant disparadoEm,
    Instant convertidoEm
) {

    public CampaignLogEntry {
        if (automationId == null) {
            throw new IllegalArgumentException("automationId é obrigatório");
        }
        if (customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório");
        }
    }

    /** Cria uma nova entrada de log (sem id, status PENDENTE_INTEGRACAO, disparadoEm agora, sem conversão). */
    public static CampaignLogEntry create(Long automationId, Long customerId) {
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.PENDENTE_INTEGRACAO,
                Instant.now(), null);
    }

    /** Reconstitui uma entrada de log a partir de persistência. */
    public static CampaignLogEntry of(Long id, Long automationId, Long customerId, CampaignDispatchStatus status,
            Instant disparadoEm, Instant convertidoEm) {
        return new CampaignLogEntry(id, automationId, customerId, status, disparadoEm, convertidoEm);
    }
}
