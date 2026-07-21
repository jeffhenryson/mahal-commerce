package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.CampaignAutomation;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de automações de campanha do CRM.
 */
public interface CampaignAutomationRepository {

    Optional<CampaignAutomation> findById(Long id);

    List<CampaignAutomation> findAll();

    CampaignAutomation save(CampaignAutomation automation);

    void deleteById(Long id);
}
