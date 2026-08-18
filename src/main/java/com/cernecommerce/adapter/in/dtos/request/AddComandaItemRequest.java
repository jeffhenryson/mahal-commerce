package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item lançado numa comanda aberta. Sem preço — igual a {@code SaleItemRequest}, o servidor
 * resolve preço e custo pelo catálogo.
 */
@Data
public class AddComandaItemRequest {

    @NotBlank
    @Schema(description = "SKU do catálogo. O preço é resolvido pelo servidor.", example = "ESS-MENTA-50")
    private String sku;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Quantidade lançada.", example = "1")
    private BigDecimal quantity;
}
