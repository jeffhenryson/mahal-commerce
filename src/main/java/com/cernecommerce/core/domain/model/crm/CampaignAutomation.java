package com.cernecommerce.core.domain.model.crm;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * Regra de automação/campanha do CRM. Quando {@code webhookUrl} está configurado, disparar a
 * automação envia de verdade um POST para essa URL (payload compatível com o que o navegador do
 * admin enviava antes — ver crm/webhook-disparo-real, F008); sem {@code webhookUrl}, o disparo
 * apenas registra {@link CampaignLogEntry} com status {@code PENDENTE_INTEGRACAO} (comportamento
 * legado).
 */
public record CampaignAutomation(
    Long id,
    String nome,
    CampaignTrigger gatilho,
    CustomerStage segmentoAlvo,
    CampaignChannel canal,
    String template,
    boolean ativa,
    Instant criadoEm,
    String webhookUrl,
    Map<String, String> webhookHeaders
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
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                URI.create(webhookUrl);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("webhookUrl possui formato inválido");
            }
        }
        webhookHeaders = webhookHeaders == null ? Map.of() : Map.copyOf(webhookHeaders);
    }

    /** Cria uma nova automação (sem id, ativa por padrão, criadoEm no momento atual, sem webhook). */
    public static CampaignAutomation create(String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template) {
        return new CampaignAutomation(null, nome, gatilho, segmentoAlvo, canal, template, true, Instant.now(),
                null, Map.of());
    }

    /** Reconstitui uma automação a partir de persistência. */
    public static CampaignAutomation of(Long id, String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template, boolean ativa, Instant criadoEm, String webhookUrl,
            Map<String, String> webhookHeaders) {
        return new CampaignAutomation(id, nome, gatilho, segmentoAlvo, canal, template, ativa, criadoEm,
                webhookUrl, webhookHeaders);
    }

    /** Retorna uma cópia desta automação com a flag ativa/inativa atualizada. */
    public CampaignAutomation withAtiva(boolean novaAtiva) {
        return new CampaignAutomation(id, nome, gatilho, segmentoAlvo, canal, template, novaAtiva, criadoEm,
                webhookUrl, webhookHeaders);
    }

    /** Retorna uma cópia desta automação com todos os campos editáveis atualizados (PUT). */
    public CampaignAutomation withDetails(String novoNome, CampaignTrigger novoGatilho,
            CustomerStage novoSegmentoAlvo, CampaignChannel novoCanal, String novoTemplate, String novoWebhookUrl,
            Map<String, String> novoWebhookHeaders) {
        return new CampaignAutomation(id, novoNome, novoGatilho, novoSegmentoAlvo, novoCanal, novoTemplate, ativa,
                criadoEm, novoWebhookUrl, novoWebhookHeaders);
    }

    /** Indica se a automação tem um webhook configurado — dispara de verdade quando true. */
    public boolean hasWebhook() {
        return webhookUrl != null && !webhookUrl.isBlank();
    }
}
