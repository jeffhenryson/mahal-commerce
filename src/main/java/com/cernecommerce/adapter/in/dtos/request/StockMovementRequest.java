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

    /**
     * Em {@code ENTRADA}/{@code SAIDA} é o delta movimentado; em {@code AJUSTE} é o saldo contado
     * na prateleira (EST-C009).
     *
     * <p>O mínimo é inclusivo porque {@code AJUSTE} para zero é válido — item que acabou. Zero em
     * {@code ENTRADA}/{@code SAIDA} continua recusado, mas pelo invariante de {@code StockMovement}
     * (400 {@code BAD_REQUEST}), já que Bean Validation não expressa regra condicional a outro
     * campo.</p>
     */
    @NotNull
    @DecimalMin(value = "0.0")
    private BigDecimal quantity;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
