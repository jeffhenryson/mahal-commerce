package com.cernecommerce.core.domain.exception.crm;

public class AutomationWebhookNotConfiguredException extends RuntimeException {
    public AutomationWebhookNotConfiguredException(Long automationId) {
        super("Automação " + automationId + " não possui webhookUrl configurado");
    }
}
