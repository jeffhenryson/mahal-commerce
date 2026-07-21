package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Trilha de auditoria de uma transição de estágio de um cliente no Kanban de CRM.
 */
public record StageTransition(
    Long id,
    Long customerId,
    CustomerStage de,
    CustomerStage para,
    String autor,
    Instant transicionadoEm
) {

    public StageTransition {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório");
        }
        if (de == null || para == null) {
            throw new IllegalArgumentException("de e para são obrigatórios");
        }
        if (de == para) {
            throw new IllegalArgumentException("de e para não podem ser o mesmo estágio");
        }
        if (autor == null || autor.isBlank()) {
            throw new IllegalArgumentException("autor é obrigatório");
        }
    }

    /** Cria uma nova transição (sem id, transicionadoEm no momento atual). */
    public static StageTransition create(Long customerId, CustomerStage de, CustomerStage para, String autor) {
        return new StageTransition(null, customerId, de, para, autor, Instant.now());
    }

    /** Reconstitui uma transição a partir de persistência. */
    public static StageTransition of(Long id, Long customerId, CustomerStage de, CustomerStage para, String autor,
            Instant transicionadoEm) {
        return new StageTransition(id, customerId, de, para, autor, transicionadoEm);
    }
}
