package com.cernecommerce.core.domain.exception.crm;

public class DuplicateTagNameException extends RuntimeException {
    public DuplicateTagNameException(String nome) {
        super("Tag já cadastrada: " + nome);
    }
}
