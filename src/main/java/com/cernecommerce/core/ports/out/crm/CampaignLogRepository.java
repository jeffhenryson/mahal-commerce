package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;

import java.util.List;

/**
 * Port de saída para o log de disparos de automações de campanha do CRM.
 */
public interface CampaignLogRepository {

    CampaignLogEntry save(CampaignLogEntry entry);

    /** Lista as entradas de log de uma automação, mais recentes primeiro. */
    List<CampaignLogEntry> findByAutomationId(Long automationId);
}
