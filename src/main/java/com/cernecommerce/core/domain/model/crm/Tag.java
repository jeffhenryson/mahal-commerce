package com.cernecommerce.core.domain.model.crm;

/**
 * Tag de segmentação livre do CRM, associável a múltiplos clientes.
 */
public record Tag(Long id, String nome) {

    public Tag {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
    }

    /** Cria uma nova tag (sem id). */
    public static Tag create(String nome) {
        return new Tag(null, nome);
    }

    /** Reconstitui uma tag a partir de persistência. */
    public static Tag of(Long id, String nome) {
        return new Tag(id, nome);
    }
}
