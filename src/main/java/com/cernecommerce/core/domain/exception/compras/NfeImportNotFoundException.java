package com.cernecommerce.core.domain.exception.compras;

public class NfeImportNotFoundException extends RuntimeException {
    public NfeImportNotFoundException(Long id) {
        super("Import de NF-e não encontrado: " + id);
    }
}
