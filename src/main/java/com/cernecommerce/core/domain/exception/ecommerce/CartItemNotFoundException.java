package com.cernecommerce.core.domain.exception.ecommerce;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String sku) {
        super("Item não encontrado no carrinho: " + sku);
    }
}
