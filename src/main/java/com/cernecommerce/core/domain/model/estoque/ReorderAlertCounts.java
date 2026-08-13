package com.cernecommerce.core.domain.model.estoque;

/**
 * Contagem de SKUs em alerta de reposição, por severidade ({@link AlertSeverity}), agregada em
 * todos os depósitos — usado pelo KPI de resumo do catálogo ({@code GET /estoque/summary}).
 */
public record ReorderAlertCounts(long criticos, long atencao) {

    public static final ReorderAlertCounts ZERO = new ReorderAlertCounts(0, 0);
}
