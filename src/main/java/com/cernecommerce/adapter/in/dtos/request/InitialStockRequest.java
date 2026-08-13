package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Estoque inicial opcional em {@code POST /estoque/products} (EST-F023). Quando informado, a
 * entrada é registrada na MESMA transação da criação do produto.
 *
 * <p>{@code quantity} é estritamente positiva — sempre um {@code ENTRADA}, ao contrário de
 * {@code StockMovementRequest.quantity}, que também aceita zero para {@code AJUSTE}.</p>
 */
@Data
public class InitialStockRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    private String warehouseCode;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal quantity;

    /** Lote recebido, quando o produto é lote-rastreado. Mesma regra de {@code StockMovementRequest.lotCode}. */
    private String lotCode;

    /** Validade do lote recebido — ver {@link #lotCode}. */
    private LocalDate expiryDate;
}
