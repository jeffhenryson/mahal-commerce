package com.cernecommerce.core.domain.exception.estoque;

public class ProductVariantNotFoundException extends RuntimeException {
    public ProductVariantNotFoundException(String productSku, String variantSku) {
        super("Variação " + variantSku + " não encontrada no produto " + productSku);
    }
}
