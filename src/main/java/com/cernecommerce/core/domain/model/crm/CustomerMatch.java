package com.cernecommerce.core.domain.model.crm;

import java.util.Set;

/**
 * Cliente achado pelo lookup (CRM-C007), com os identificadores que bateram — o front usa
 * {@code matchedBy} para dizer ao operador "já existe um cliente com este telefone".
 */
public record CustomerMatch(Customer customer, Set<CustomerMatchField> matchedBy) {

    public CustomerMatch {
        matchedBy = Set.copyOf(matchedBy);
    }
}
