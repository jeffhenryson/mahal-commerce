package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
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

    @Schema(description = "Por que a linha existe (PDV-F010). Omitido resolve para NORMAL. "
            + "OPEN_ROSH cobra o openRoshPrice do produto PAI, não o preço da variação do sabor. "
            + "SABOR_EXTRA e TROCA exigem linkedItemId.",
            example = "OPEN_ROSH")
    private ConsumptionMode mode;

    @Schema(description = "Linha a preço zero que AINDA baixa estoque — a promo \"pague 1 leve 2\" "
            + "e a troca de sabor durante um open rosh. Exige a permissão PDV_COMANDA_COURTESY: "
            + "lançar linha a zero é um desconto de 100%. TROCA é cortesia mesmo sem este campo.",
            example = "false")
    private Boolean courtesy;

    @Schema(description = "Linha de origem NESTA comanda: a sessão que este segundo sabor "
            + "acompanha, ou o open rosh a que esta troca pertence. Obrigatório em SABOR_EXTRA e "
            + "TROCA, recusado nos demais modos.",
            example = "17")
    private Long linkedItemId;
}
