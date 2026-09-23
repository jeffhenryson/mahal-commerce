package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cotação de um kit montado (EST-F031): cada linha com o preço vigente do catálogo e a sua parte
 * do desconto do kit, já rateada ao centavo — a soma de {@code discountAmount} das linhas é
 * exatamente {@code discount}.
 *
 * <p>O desconto vai por linha, e não como um número solto do pacote, pela mesma razão de
 * {@code DiscountProration}: cashback e margem são calculados sobre o líquido de cada item.</p>
 */
public record KitQuote(KitTemplate template, List<Line> lines, BigDecimal subtotal, BigDecimal discount,
        BigDecimal total) {

    public KitQuote {
        lines = List.copyOf(lines);
    }

    public record Line(Long stepId, String stepName, String sku, String productName, BigDecimal unitPrice,
            BigDecimal discountAmount) {

        public BigDecimal netAmount() {
            return unitPrice.subtract(discountAmount);
        }
    }
}
