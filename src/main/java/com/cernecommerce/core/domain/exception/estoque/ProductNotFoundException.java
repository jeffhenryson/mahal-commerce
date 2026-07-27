package com.cernecommerce.core.domain.exception.estoque;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String sku) {
        super("SKU não encontrado no catálogo: " + sku);
    }
}
