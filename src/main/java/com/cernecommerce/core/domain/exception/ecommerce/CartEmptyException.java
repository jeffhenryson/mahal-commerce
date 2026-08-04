package com.cernecommerce.core.domain.exception.ecommerce;

public class CartEmptyException extends RuntimeException {
    public CartEmptyException() {
        super("Carrinho vazio — adicione itens antes de finalizar a compra");
    }
}
