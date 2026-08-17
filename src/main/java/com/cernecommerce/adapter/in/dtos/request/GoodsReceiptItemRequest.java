package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoodsReceiptItemRequest {
    @NotBlank
    private String sku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    /** Obrigatório junto com {@code expiryDate} só quando o SKU é lote-rastreado (EST-F008). */
    private String lotCode;

    private LocalDate expiryDate;

    /** Custo unitário do recebimento (EST-F007), opcional — alimenta o custo médio ponderado. */
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal unitCost;
}
