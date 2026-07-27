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
}
