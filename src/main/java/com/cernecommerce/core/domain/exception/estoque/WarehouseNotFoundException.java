package com.cernecommerce.core.domain.exception.estoque;

public class WarehouseNotFoundException extends RuntimeException {
    public WarehouseNotFoundException(String code) {
        super("Depósito não encontrado: " + code);
    }
}
