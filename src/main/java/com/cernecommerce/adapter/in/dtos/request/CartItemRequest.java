package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Upsert de quantidade de uma linha do carrinho (ECM-F003) — PUT idempotente: define a
 * quantidade, não incrementa. O SKU vem do path, não do corpo.
 */
@Data
public class CartItemRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Quantidade desejada da linha.", example = "2.000")
    private BigDecimal quantity;
}
