package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateAttributeTypeNameException extends RuntimeException {
    public DuplicateAttributeTypeNameException(String name) {
        super("Já existe um tipo de atributo com o nome: " + name);
    }
}
