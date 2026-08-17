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
 *
 * @param unitCost custo unitário do recebimento (EST-F007), opcional — propagado para
 *        {@code EstoqueUseCase.adjustStock} como custo de entrada, que alimenta o custo médio
 *        ponderado do SKU. {@code null} quando o custo não é conhecido no momento do recebimento.
 */
public record GoodsReceiptItem(String sku, BigDecimal quantity, String lotCode, LocalDate expiryDate,
        BigDecimal unitCost) {

    public GoodsReceiptItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
    }

    /** Item sem lote nem custo — SKU não lote-rastreado, custo desconhecido. */
    public GoodsReceiptItem(String sku, BigDecimal quantity) {
        this(sku, quantity, null, null, null);
    }

    /** Item com lote, sem custo conhecido — compatibilidade com chamadores anteriores a EST-F007. */
    public GoodsReceiptItem(String sku, BigDecimal quantity, String lotCode, LocalDate expiryDate) {
        this(sku, quantity, lotCode, expiryDate, null);
    }
}
