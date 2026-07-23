package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemRequest {
    @NotBlank
    private String sku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal unitPrice;
}
