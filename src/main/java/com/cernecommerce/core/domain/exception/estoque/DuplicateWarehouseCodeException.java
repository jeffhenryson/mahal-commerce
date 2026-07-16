package com.cernecommerce.core.domain.exception.estoque;

public class DuplicateWarehouseCodeException extends RuntimeException {
    public DuplicateWarehouseCodeException(String code) {
        super("Código de depósito já cadastrado: " + code);
    }
}
