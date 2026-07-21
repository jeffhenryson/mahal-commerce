package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Nota/interação registrada para um cliente do CRM.
 */
public record CustomerNote(
    Long id,
    Long customerId,
    String autor,
    String texto,
    Instant criadoEm
) {

    public CustomerNote {
        if (customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório");
        }
        if (autor == null || autor.isBlank()) {
            throw new IllegalArgumentException("autor é obrigatório");
        }
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("texto é obrigatório");
        }
    }

    /** Cria uma nova nota (sem id, criadoEm no momento atual). */
    public static CustomerNote create(Long customerId, String autor, String texto) {
        return new CustomerNote(null, customerId, autor, texto, Instant.now());
    }

    /** Reconstitui uma nota a partir de persistência. */
    public static CustomerNote of(Long id, Long customerId, String autor, String texto, Instant criadoEm) {
        return new CustomerNote(id, customerId, autor, texto, criadoEm);
    }
}
