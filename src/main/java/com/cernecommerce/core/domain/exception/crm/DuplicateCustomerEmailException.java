package com.cernecommerce.core.domain.exception.crm;

public class DuplicateCustomerEmailException extends RuntimeException {
    public DuplicateCustomerEmailException(String email) {
        super("Email já cadastrado: " + email);
    }
}
