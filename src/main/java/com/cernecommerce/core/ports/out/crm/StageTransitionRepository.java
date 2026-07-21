package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.StageTransition;

import java.util.List;

/**
 * Port de saída para a trilha de auditoria de transições de estágio do CRM.
 */
public interface StageTransitionRepository {

    StageTransition save(StageTransition transition);

    /** Lista as transições de um cliente, mais recentes primeiro. */
    List<StageTransition> findByCustomerId(Long customerId);
}
