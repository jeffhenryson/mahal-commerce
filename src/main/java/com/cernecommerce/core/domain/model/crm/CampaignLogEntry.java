package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Entrada do log de disparo de uma automação de campanha para um cliente-alvo. {@code erroDetalhe}
 * só é preenchido quando {@code status == FALHA} — mensagem de erro truncada da tentativa de
 * disparo real do webhook. {@code convertidoEm} fica sempre {@code null} nesta versão — rastrear
 * conversão depende do domínio de pedidos, que ainda não existe no backend (ver
 * crm/listagem-clientes-rfm).
 */
public record CampaignLogEntry(
    Long id,
    Long automationId,
    Long customerId,
    CampaignDispatchStatus status,
    Instant disparadoEm,
    Instant convertidoEm,
    String erroDetalhe
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
                Instant.now(), null, null);
    }

    /** Cria uma entrada de log para um disparo real bem-sucedido (HTTP 2xx do webhook). */
    public static CampaignLogEntry enviado(Long automationId, Long customerId) {
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.ENVIADO,
                Instant.now(), null, null);
    }

    /** Cria uma entrada de log para um disparo real que falhou — erro de rede, timeout ou HTTP não-2xx. */
    public static CampaignLogEntry falha(Long automationId, Long customerId, String erroDetalhe) {
        return new CampaignLogEntry(null, automationId, customerId, CampaignDispatchStatus.FALHA,
                Instant.now(), null, erroDetalhe);
    }

    /** Reconstitui uma entrada de log a partir de persistência. */
    public static CampaignLogEntry of(Long id, Long automationId, Long customerId, CampaignDispatchStatus status,
            Instant disparadoEm, Instant convertidoEm, String erroDetalhe) {
        return new CampaignLogEntry(id, automationId, customerId, status, disparadoEm, convertidoEm, erroDetalhe);
    }
}
