package com.cernecommerce.core.domain.exception.estoque;

public class KitTemplateNotFoundException extends RuntimeException {
    public KitTemplateNotFoundException(Long id) {
        super("Kit montável não encontrado: " + id);
    }
}
