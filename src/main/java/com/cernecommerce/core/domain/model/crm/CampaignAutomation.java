package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Regra de automação/campanha do CRM. O disparo real (envio por WhatsApp/E-mail) depende da
 * integração de canal de envio (crm/integracao-canal-envio, F008, ainda pendente) — nesta
 * versão, disparar uma automação apenas registra {@link CampaignLogEntry} por cliente-alvo.
 */
public record CampaignAutomation(
    Long id,
    String nome,
    CampaignTrigger gatilho,
    CustomerStage segmentoAlvo,
    CampaignChannel canal,
    String template,
    boolean ativa,
    Instant criadoEm
) {

    public CampaignAutomation {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        if (gatilho == null) {
            throw new IllegalArgumentException("gatilho é obrigatório");
        }
        if (segmentoAlvo == null) {
            throw new IllegalArgumentException("segmentoAlvo é obrigatório");
        }
        if (canal == null) {
            throw new IllegalArgumentException("canal é obrigatório");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template é obrigatório");
        }
    }

    /** Cria uma nova automação (sem id, ativa por padrão, criadoEm no momento atual). */
    public static CampaignAutomation create(String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template) {
        return new CampaignAutomation(null, nome, gatilho, segmentoAlvo, canal, template, true, Instant.now());
    }

    /** Reconstitui uma automação a partir de persistência. */
    public static CampaignAutomation of(Long id, String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template, boolean ativa, Instant criadoEm) {
        return new CampaignAutomation(id, nome, gatilho, segmentoAlvo, canal, template, ativa, criadoEm);
    }

    /** Retorna uma cópia desta automação com a flag ativa/inativa atualizada. */
    public CampaignAutomation withAtiva(boolean novaAtiva) {
        return new CampaignAutomation(id, nome, gatilho, segmentoAlvo, canal, template, novaAtiva, criadoEm);
    }
}
