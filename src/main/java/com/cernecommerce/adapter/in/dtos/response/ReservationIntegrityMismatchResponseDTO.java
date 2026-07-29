package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Linha de diagnóstico EST-C013: divergência entre o contador de reservado em
 * {@code stock_balance} e a soma das reservas {@code ACTIVE} no ledger {@code stock_reservation}
 * para o mesmo par SKU/depósito.
 */
@Data
public class ReservationIntegrityMismatchResponseDTO {
    private String sku;
    private String warehouseCode;
    /** {@code stock_balance.reserved_quantity} — o contador. */
    private BigDecimal reservedQuantity;
    /** Soma de {@code quantity} das reservas {@code ACTIVE} no ledger. */
    private BigDecimal activeReservationsTotal;
    /** {@code reservedQuantity - activeReservationsTotal}. */
    private BigDecimal difference;
}
