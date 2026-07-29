package com.cernecommerce.core.domain.exception.crm;

public class DuplicateCustomerCpfException extends RuntimeException {
    public DuplicateCustomerCpfException(String cpf) {
        super("CPF já cadastrado: " + cpf);
    }
}
