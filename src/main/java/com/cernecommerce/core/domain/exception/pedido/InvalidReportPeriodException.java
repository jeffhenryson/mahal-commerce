package com.cernecommerce.core.domain.exception.pedido;

/**
 * Período inválido para GET /orders/summary: {@code from} depois de {@code to}, ou intervalo
 * maior que o teto permitido. Sem essa validação a agregação varreria a tabela de pedidos
 * inteira — a consulta mais cara do domínio.
 */
public class InvalidReportPeriodException extends RuntimeException {

    public InvalidReportPeriodException(String message) {
        super(message);
    }
}
