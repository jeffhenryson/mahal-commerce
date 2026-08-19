package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma entrada do histórico de compras de um SKU (item 2 do pedido do frontend) — uma
 * {@code ENTRADA} de estoque, enriquecida com o fornecedor quando ela veio de um recebimento.
 *
 * <p>{@code supplierId}/{@code supplierName}/{@code goodsReceiptId} ficam nulos em entradas
 * manuais e em toda entrada anterior à migration que introduziu o vínculo — limitação conhecida
 * de dado histórico, não erro.</p>
 */
@Data
public class PurchaseHistoryResponseDTO {
    private BigDecimal quantity;
    private BigDecimal unitCost;
    private Instant purchasedAt;
    private Long supplierId;
    private String supplierName;
    private Long goodsReceiptId;
    private String lotCode;
}
