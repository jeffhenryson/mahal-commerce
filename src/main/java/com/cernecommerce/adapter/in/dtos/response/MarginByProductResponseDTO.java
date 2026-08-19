package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MarginByProductResponseDTO {

    private String sku;

    @Schema(description = "Cai para o próprio SKU quando o produto foi renomeado ou excluído "
            + "depois da venda — não há FK entre item de pedido e produto.")
    private String productName;

    private BigDecimal quantitySold;
    private BigDecimal margin;
}
