package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "Anota (ou reanota) um item na lista de reposição de um depósito")
public class ReplenishmentItemRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @NotBlank
    @Size(min = 2, max = 50)
    private String warehouseCode;

    @NotNull
    @PositiveOrZero
    private BigDecimal quantity;

    @Size(max = 500)
    private String note;
}
