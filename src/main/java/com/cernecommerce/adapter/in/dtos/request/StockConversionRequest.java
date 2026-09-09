package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Conversão de saldo entre dois SKUs no mesmo depósito (EST-F025) — ex.: 1 lata de essência vira 5
 * sessões de narguilé.
 *
 * <p>As quantidades das duas pontas são independentes e ambas obrigatórias: é a razão de existir da
 * operação (1 entra, N saem). {@code toQuantity} não é derivado de {@code sessionsPerUnit} do
 * catálogo de propósito — ver o javadoc de {@code EstoqueUseCase.convertStock}.</p>
 *
 * <p>Os dois {@code DecimalMin} são {@code inclusive = false}: converter zero de alguma coisa não é
 * uma conversão, é um par de linhas vazias no ledger. Contrasta com {@code StockMovementRequest},
 * onde zero é válido — lá um {@code AJUSTE} de zero é o item que acabou.</p>
 */
@Data
public class StockConversionRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String fromSku;

    @NotBlank
    @Size(min = 3, max = 50)
    private String toSku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal fromQuantity;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal toQuantity;

    @NotBlank
    @Size(min = 2, max = 50)
    private String warehouseCode;

    @NotBlank
    @Size(max = 255)
    private String reason;
}
