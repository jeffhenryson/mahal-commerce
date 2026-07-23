package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockMovementRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @NotBlank
    private String warehouseCode;

    @NotBlank
    private String type;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
