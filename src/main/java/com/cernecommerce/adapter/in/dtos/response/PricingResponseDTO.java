package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Precificação de um produto (EST-F019): os três campos armazenados mais os derivados que a UI
 * precisaria recalcular sozinha — e recalcularia com regra de arredondamento diferente da do
 * backend, produzindo divergência de centavo entre a tela e o caixa.
 *
 * <p>Campos derivados vêm nulos quando indefinidos (custo ausente, preço ausente, ou divisão
 * por zero) — a ausência é informação: significa "não há como calcular", não "vale zero".</p>
 */
@Data
@Schema(description = "Precificação do produto, com os valores derivados já calculados")
public class PricingResponseDTO {

    @Schema(description = "Custo de aquisição por unidade", example = "45.00")
    private BigDecimal costPrice;

    @Schema(description = "Markup pretendido sobre o custo, em %", example = "80.0000")
    private BigDecimal markupPercent;

    @Schema(description = "Preço praticado, quando cadastrado explicitamente", example = "79.90")
    private BigDecimal salePrice;

    @Schema(description = "Preço \"de\" riscado na vitrine (\"de/por\"), quando cadastrado", example = "99.90")
    private BigDecimal originalPrice;

    @Schema(description = "Preço que o markup manda cobrar: custo × (1 + markup/100)", example = "81.00")
    private BigDecimal suggestedPrice;

    @Schema(description = "Preço a cobrar de fato: o praticado se houver, senão o sugerido. "
            + "É o valor que o PDV deve consumir.", example = "79.90")
    private BigDecimal effectivePrice;

    @Schema(description = "Lucro bruto por unidade: preço efetivo − custo", example = "34.90")
    private BigDecimal marginAmount;

    @Schema(description = "Margem sobre a VENDA, em % — a fatia do faturamento que sobra", example = "43.68")
    private BigDecimal marginPercent;

    @Schema(description = "Markup que o preço efetivo realmente representa, em % — difere do "
            + "pretendido quando o preço praticado foi arredondado", example = "77.56")
    private BigDecimal effectiveMarkupPercent;

    @Schema(description = "Indica se há preço a cobrar (praticado ou sugerido)", example = "true")
    private boolean priced;

    @Schema(description = "Indica se o preço efetivo está abaixo do custo — prejuízo por unidade. "
            + "Permitido pelo domínio; sinalizado para a UI avisar.", example = "false")
    private boolean belowCost;

    @Schema(description = "Indica se há desconto \"de/por\" real a exibir — só true quando "
            + "originalPrice é maior que o preço efetivo", example = "true")
    private boolean hasDiscount;

    @Schema(description = "Percentual de desconto do preço \"de/por\": (originalPrice - "
            + "effectivePrice) / originalPrice × 100. Nulo quando hasDiscount é falso.",
            example = "20.02")
    private BigDecimal discountPercent;
}
