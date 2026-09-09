package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.AbcClass;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** Uma linha da curva ABC (EST-F011). */
@Data
public class AbcEntryResponseDTO {

    private String sku;
    private String productName;

    @Schema(description = "Quantidade que saiu do estoque no período (inclui cortesia, perda e "
            + "conversão — tudo que precisa ser reposto).")
    private BigDecimal consumedQuantity;

    @Schema(description = "Quantidade × custo médio vigente. SKU sem custo médio conhecido vale "
            + "zero e cai em C, em vez de sumir do relatório.")
    private BigDecimal consumedValue;

    @Schema(description = "Percentual acumulado do valor total, somando esta linha e todas as "
            + "acima dela. A última linha fecha em 100.", example = "82.50")
    private BigDecimal cumulativePercent;

    @Schema(description = "A até 80% do acumulado, B até 95%, C o resto.")
    private AbcClass abcClass;

    @Schema(description = "Consumo do período ÷ saldo atual. **Nulo quando o saldo é zero**: um SKU "
            + "que acabou não tem giro infinito, tem giro desconhecido.", nullable = true)
    private BigDecimal turnover;
}
