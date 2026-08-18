package com.cernecommerce.core.domain.exception.financeiro;

/**
 * Período inválido para GET /financeiro/cash-flow/summary: {@code from} depois de {@code to},
 * ou intervalo maior que o teto permitido — mesma defesa de {@code OrderReportService}, a
 * consulta agregada mais cara do domínio financeiro.
 */
public class InvalidReportPeriodException extends RuntimeException {

    public InvalidReportPeriodException(String message) {
        super(message);
    }
}
