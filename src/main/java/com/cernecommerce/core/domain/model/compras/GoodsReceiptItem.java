package com.cernecommerce.core.domain.model.compras;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Item de um {@link GoodsReceipt}: quantidade recebida de um SKU.
 *
 * <p>{@code lotCode}/{@code expiryDate} (EST-F008) só se aplicam quando o SKU é
 * {@code lotTracked} — nesse caso são obrigatórios, e a validação mora em
 * {@code EstoqueService.validateLotInfo}, a mesma régua de {@code adjustStock}. SKU não
 * lote-rastreado usa o construtor de dois argumentos, que os deixa nulos.</p>
 */
public record GoodsReceiptItem(String sku, BigDecimal quantity, String lotCode, LocalDate expiryDate) {

    public GoodsReceiptItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
    }

    /** Item sem lote — SKU não lote-rastreado. */
    public GoodsReceiptItem(String sku, BigDecimal quantity) {
        this(sku, quantity, null, null);
    }
}
