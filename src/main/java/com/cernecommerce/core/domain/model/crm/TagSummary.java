package com.cernecommerce.core.domain.model.crm;

/**
 * Tag com a contagem de clientes associados — usada na listagem de tags (`GET /crm/tags`).
 */
public record TagSummary(Long id, String nome, long clientesCount) {}
