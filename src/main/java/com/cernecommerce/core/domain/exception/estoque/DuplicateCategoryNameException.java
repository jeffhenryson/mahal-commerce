package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateCategoryNameException extends RuntimeException {
    public DuplicateCategoryNameException(String name) {
        super("Já existe uma categoria com o nome: " + name);
    }
}
