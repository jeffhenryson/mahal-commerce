package com.cernecommerce.core.domain.model.estoque;

/** Severidade de um alerta de reposição de estoque. Ver {@link ReorderPoint#severityFor}. */
public enum AlertSeverity {
    OK,
    ATENCAO,
    CRITICO
}
