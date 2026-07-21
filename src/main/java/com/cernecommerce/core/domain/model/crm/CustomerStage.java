package com.cernecommerce.core.domain.model.crm;

/**
 * Estágio manual de um cliente no funil de atendimento (Kanban de CRM).
 * Independente do segmento RFM auto-calculado (ver {@code CustomerResponseDTO.segmento}).
 */
public enum CustomerStage {
    NOVO_LEAD,
    EM_ATENDIMENTO,
    QUALIFICADO,
    CLIENTE_ATIVO,
    INATIVO
}
