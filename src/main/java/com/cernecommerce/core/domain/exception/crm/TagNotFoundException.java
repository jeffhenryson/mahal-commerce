package com.cernecommerce.core.domain.exception.crm;

public class TagNotFoundException extends RuntimeException {
    public TagNotFoundException(Long id) {
        super("Tag não encontrada: " + id);
    }
}
