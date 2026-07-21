package com.cernecommerce.core.domain.exception.crm;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Cliente não encontrado: " + id);
    }
}
