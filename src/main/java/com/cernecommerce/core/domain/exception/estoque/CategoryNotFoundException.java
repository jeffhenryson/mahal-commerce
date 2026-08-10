package com.cernecommerce.core.domain.exception.estoque;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(Long id) {
        super("Categoria não encontrada: " + id);
    }
}
