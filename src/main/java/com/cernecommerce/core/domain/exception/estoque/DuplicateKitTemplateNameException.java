package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateKitTemplateNameException extends RuntimeException {
    public DuplicateKitTemplateNameException(String name) {
        super("Já existe um kit montável com o nome: " + name);
    }
}
