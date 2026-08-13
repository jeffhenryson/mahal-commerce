package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Precificação de um produto (EST-F019). Os três campos são opcionais, tanto na criação quanto
 * no PATCH — produto sem preço é estado válido do catálogo.
 *
 * <p>No PATCH, cada campo segue a semântica do resto do módulo: ausente ou nulo significa
 * <b>não mexer</b>.</p>
 */
@Data
@Schema(description = "Custo, markup e preço praticado de um produto")
public class PricingRequest {

    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Custo de aquisição por unidade", example = "45.00")
    private BigDecimal costPrice;

    @PositiveOrZero
    @Digits(integer = 5, fraction = 4)
    @Schema(description = "Markup desejado sobre o CUSTO, em % (100 = dobrar o custo). "
            + "Não confundir com margem, que é sobre a venda.", example = "80")
    private BigDecimal markupPercent;

    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Preço praticado. Quando informado, vence sobre o sugerido pelo markup — "
            + "é onde entra o preço psicológico. Omitido, o preço efetivo passa a ser o sugerido.",
            example = "79.90")
    private BigDecimal salePrice;

    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Preço \"de\" riscado na vitrine, para efeito de desconto (\"de/por\"). "
            + "Puramente valor de exibição — sem relação obrigatória com o preço praticado.",
            example = "99.90")
    private BigDecimal originalPrice;

    @PositiveOrZero
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Preço extraordinário: valor adicional opcional destinado a uma causa, "
            + "por fora do preço de venda. Puramente informativo — não soma no preço efetivo.",
            example = "2.00")
    private BigDecimal causeAmount;
}
