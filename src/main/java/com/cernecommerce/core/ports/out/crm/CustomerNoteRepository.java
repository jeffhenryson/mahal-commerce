package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.CustomerNote;

import java.util.List;

/**
 * Port de saída para persistência de notas de clientes do CRM.
 */
public interface CustomerNoteRepository {

    CustomerNote save(CustomerNote note);

    /** Lista as notas de um cliente, mais recentes primeiro. */
    List<CustomerNote> findByCustomerId(Long customerId);
}
