package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Registro do que foi contado de um SKU num balanço (EST-F006). */
@Data
public class StockCountItemRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    /**
     * O que foi contado na prateleira. Mínimo <b>inclusivo</b>: contar zero é o caso legítimo do
     * item que acabou ou sumiu, e é justamente o que o balanço precisa registrar.
     */
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal countedQuantity;
}
