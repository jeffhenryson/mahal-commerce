package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class KitComponentRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String componentSku;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal quantity;
}
