package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Ponto de reposição (quantidade mínima) de um SKU em um depósito. Quando o
 * {@link StockBalance} correspondente cai abaixo de {@code minQuantity}, uma notificação de
 * reposição deve ser disparada.
 */
public record ReorderPoint(Long id, String sku, Long warehouseId, BigDecimal minQuantity) {

    /**
     * Fração de {@link #minQuantity} abaixo da qual o alerta vira {@link AlertSeverity#CRITICO}
     * em vez de {@link AlertSeverity#ATENCAO}. Vive aqui, visível em Java, porque
     * {@code EstoqueService.getSummary} replica a mesma regra em SQL por performance — duplicar a
     * aritmética é aceitável, duplicar o valor do limiar sem um lugar canônico para retunar não é.
     */
    public static final BigDecimal CRITICAL_THRESHOLD_FACTOR = new BigDecimal("0.5");

    public ReorderPoint {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (minQuantity == null || minQuantity.signum() < 0) {
            throw new IllegalArgumentException("minQuantity deve ser maior ou igual a zero");
        }
    }

    /** Cria um novo ponto de reposição (sem id). */
    public static ReorderPoint create(String sku, Long warehouseId, BigDecimal minQuantity) {
        return new ReorderPoint(null, sku, warehouseId, minQuantity);
    }

    /** Reconstitui um ponto de reposição a partir de persistência. */
    public static ReorderPoint of(Long id, String sku, Long warehouseId, BigDecimal minQuantity) {
        return new ReorderPoint(id, sku, warehouseId, minQuantity);
    }

    /** {@code true} se {@code quantity} está estritamente abaixo do ponto de reposição. */
    public boolean isBelow(BigDecimal quantity) {
        return quantity.compareTo(minQuantity) < 0;
    }

    /**
     * Classifica a severidade do alerta para {@code quantity} neste ponto de reposição — usada
     * pelo painel de KPIs ({@code GET /estoque/summary}).
     *
     * <p>{@link AlertSeverity#CRITICO}: saldo zerado/negativo, ou igual ou abaixo de
     * {@link #CRITICAL_THRESHOLD_FACTOR} do mínimo configurado. {@link AlertSeverity#ATENCAO}:
     * abaixo do mínimo, mas acima do limiar crítico. {@link AlertSeverity#OK}: no mínimo ou acima.</p>
     */
    public AlertSeverity severityFor(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            return AlertSeverity.CRITICO;
        }
        if (quantity.compareTo(minQuantity.multiply(CRITICAL_THRESHOLD_FACTOR)) <= 0) {
            return AlertSeverity.CRITICO;
        }
        if (isBelow(quantity)) {
            return AlertSeverity.ATENCAO;
        }
        return AlertSeverity.OK;
    }
}
