package com.cernecommerce.core.ports.in;

import java.util.Map;

/**
 * Renderiza variáveis {@code {{cliente.nome}}} em templates de automação do CRM. Porta própria
 * (em vez de método solto em {@code CrmUseCase}) porque é lógica pura, reaproveitável e
 * testável isoladamente, sem depender de nenhum repositório.
 */
public interface CampaignTemplateRendererUseCase {

    String render(String template, Map<String, Object> variables);
}
