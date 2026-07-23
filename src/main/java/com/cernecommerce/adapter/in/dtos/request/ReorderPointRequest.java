package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReorderPointRequest {
    @NotBlank
    private String warehouseCode;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal minQuantity;
}
