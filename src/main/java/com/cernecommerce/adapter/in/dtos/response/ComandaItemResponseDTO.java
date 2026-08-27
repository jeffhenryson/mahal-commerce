package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class ComandaItemResponseDTO {

    private Long id;
    private String sku;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
    private BigDecimal subtotal;
    private Instant addedAt;

    @Schema(description = "Por que a linha existe: NORMAL, OPEN_ROSH, SABOR_EXTRA ou TROCA "
            + "(PDV-F010). Sem isto a tela não sabe quais linhas aceitam troca de sabor.")
    private String mode;

    @Schema(description = "Linha a preço zero que ainda baixou estoque. Campo próprio, não "
            + "inferido de unitPrice = 0 — um desconto de 100% dá o mesmo zero.")
    private boolean courtesy;

    @Schema(description = "Linha de origem nesta comanda. Preenchido em SABOR_EXTRA e TROCA.")
    private Long linkedItemId;
}
