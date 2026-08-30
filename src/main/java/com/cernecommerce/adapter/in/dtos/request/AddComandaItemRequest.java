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

    // Sem @Size de propósito (PDV-F011): a Bean Validation devolveria o código genérico de
    // validação, e o contrato negociado com o cliente pede NOTES_TOO_LONG. A recusa é do service.
    @Schema(description = "Registro livre do que saiu para a mesa: qual narguilé, com ou sem "
            + "filtro, QUAL PINÇA (PDV-F011). Máximo 200 caracteres — acima disso é recusado com "
            + "NOTES_TOO_LONG, não truncado, para o operador saber que perderia o registro. Texto "
            + "opaco: o servidor grava e devolve, nunca interpreta. SEM NENHUM EFEITO EM PREÇO — a "
            + "pinça não é consumida, e lançá-la como cortesia baixaria estoque e apareceria no "
            + "cupom do cliente como um item de R$ 0 que ele não pediu.",
            example = "Narguilé grande · Com filtro · Pinça P-02")
    private String notes;

    // Idem: sem @DecimalMin, para o negativo sair como SURCHARGE_INVALID e não como erro genérico.
    @Schema(description = "Acréscimo somado ao preço que o servidor resolve, para a essência que "
            + "sai mais cara mesmo no consumo livre (PDV-F011). Só vale em mode = OPEN_ROSH, e "
            + "soma sobre o openRoshPrice do produto PAI, não sobre o preço da variação do sabor. "
            + "NÃO é um discountAmount negativo: o relatório precisa distinguir 'cobramos a mais' "
            + "de 'cobramos a menos'. Exige a permissão PDV_COMANDA_SURCHARGE. O costPrice segue "
            + "congelado normalmente — acréscimo é margem, não custo.",
            example = "15.00")
    private BigDecimal surchargeAmount;
}
