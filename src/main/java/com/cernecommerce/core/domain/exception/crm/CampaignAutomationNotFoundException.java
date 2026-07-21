package com.cernecommerce.core.domain.exception.crm;

public class CampaignAutomationNotFoundException extends RuntimeException {
    public CampaignAutomationNotFoundException(Long id) {
        super("Automação não encontrada: " + id);
    }
}
