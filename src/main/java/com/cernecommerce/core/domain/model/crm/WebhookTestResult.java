package com.cernecommerce.core.domain.model.crm;

import java.util.Map;

/** Resultado de um disparo de teste de webhook (POST /crm/automacoes/{id}/testar) — não persiste log. */
public record WebhookTestResult(boolean success, Integer statusCode, String errorMessage,
        Map<String, Object> payloadEnviado) {
}
