package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha do ledger de movimentações de estoque. {@code warehouseCode} é o código informado na
 * consulta — o domínio guarda apenas o {@code warehouseId}.
 */
@Data
public class StockMovementResponseDTO {
    private Long id;
    private String sku;
    private String warehouseCode;
    private String type;
    private BigDecimal quantity;
    private String reason;
    private String username;
    private Instant createdAt;
    private String lotCode;
    /** Custo unitário da entrada (EST-F007). Nulo em SAIDA/AJUSTE ou entrada sem custo conhecido. */
    private BigDecimal unitCost;
    /** Unidade de medida do produto dono da movimentação. Nula quando o SKU não resolve mais a um produto. */
    private String unit;
}
