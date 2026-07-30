package com.cernecommerce.core.domain.exception.estoque;

public class EmptyKitRecipeException extends RuntimeException {
    public EmptyKitRecipeException(String kitSku) {
        super("Receita de kit não pode ser vazia: " + kitSku);
    }
}
