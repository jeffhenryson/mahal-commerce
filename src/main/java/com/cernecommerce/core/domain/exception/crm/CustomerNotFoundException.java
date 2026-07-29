package com.cernecommerce.core.domain.exception.crm;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(Long id) {
        super("Cliente não encontrado: " + id);
    }

    /** Usado pela busca de balcão (CRM-F002), quando o critério é CPF/email/contato, não id. */
    public CustomerNotFoundException(String criteria) {
        super("Cliente não encontrado: " + criteria);
    }
}
