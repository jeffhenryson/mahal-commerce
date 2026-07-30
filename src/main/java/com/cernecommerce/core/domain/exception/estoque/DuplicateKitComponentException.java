package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateKitComponentException extends RuntimeException {
    public DuplicateKitComponentException(String kitSku, String componentSku) {
        super("Componente duplicado na receita do kit " + kitSku + ": " + componentSku);
    }
}
