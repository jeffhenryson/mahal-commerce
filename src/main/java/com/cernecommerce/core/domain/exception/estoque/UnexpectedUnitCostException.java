package com.cernecommerce.core.domain.exception.estoque;

/**
 * Custo unitário informado onde não se aplica (EST-F007): a movimentação não é uma ENTRADA
 * (SAIDA/AJUSTE não recalculam custo médio), ou o SKU é um kit (kit não tem saldo próprio, logo
 * não acumula custo médio — o custo do kit continua derivado da soma dos componentes).
 */
public class UnexpectedUnitCostException extends RuntimeException {
    public UnexpectedUnitCostException(String sku, String reason) {
        super("Custo unitário não se aplica ao SKU " + sku + ": " + reason);
    }
}
