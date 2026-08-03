package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Linha de diagnóstico EST-F008: divergência entre {@code stock_balance.quantity} e a soma de
 * {@code stock_lot.quantity} para o mesmo par SKU/depósito.
 */
@Data
public class LotIntegrityMismatchResponseDTO {
    private String sku;
    private String warehouseCode;
    /** {@code stock_balance.quantity} — o agregado. */
    private BigDecimal balanceQuantity;
    /** Soma de {@code quantity} de todo {@code stock_lot} do par. */
    private BigDecimal lotsTotal;
    /** {@code balanceQuantity - lotsTotal}. */
    private BigDecimal difference;
}
