package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateSkuException extends RuntimeException {
    public DuplicateSkuException(String sku) {
        super("SKU já cadastrado: " + sku);
    }
}
