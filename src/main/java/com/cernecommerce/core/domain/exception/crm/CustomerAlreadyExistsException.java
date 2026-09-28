package com.cernecommerce.core.domain.exception.crm;

import com.cernecommerce.core.domain.model.crm.CustomerMatchField;

import java.util.Set;

/**
 * Telefone, email ou CPF do cadastro já pertencem a outro cliente (CRM-C007). Leva o cliente
 * existente e o que bateu, para o front oferecer "usar este cliente" em vez de só recusar.
 */
public class CustomerAlreadyExistsException extends RuntimeException {

    private final transient Set<CustomerMatchField> matchedBy;
    private final Long customerId;

    public CustomerAlreadyExistsException(Set<CustomerMatchField> matchedBy, Long customerId) {
        super("Cliente já cadastrado com " + matchedBy + ": " + customerId);
        this.matchedBy = Set.copyOf(matchedBy);
        this.customerId = customerId;
    }

    public Set<CustomerMatchField> getMatchedBy() {
        return matchedBy;
    }

    public Long getCustomerId() {
        return customerId;
    }
}
