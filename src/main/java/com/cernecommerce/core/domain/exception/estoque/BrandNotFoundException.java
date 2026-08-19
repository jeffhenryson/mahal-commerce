package com.cernecommerce.core.domain.exception.estoque;

public class BrandNotFoundException extends RuntimeException {
    public BrandNotFoundException(Long id) {
        super("Marca não encontrada: " + id);
    }
}
