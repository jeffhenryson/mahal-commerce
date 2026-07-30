package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;

/**
 * Linha do diagnóstico "produtos cuja taxa vigente consome mais de X% da margem" (CRM-F003,
 * §2.4). Usa {@code Pricing.marginPercent()}, sem matemática nova — só cruza com a taxa
 * resolvida para o SKU.
 *
 * @param marginShareConsumed {@code cashbackPercent / marginPercent * 100} — a fatia da margem
 *        que a taxa de cashback come. É o que expõe, por exemplo, um carvão a 8% comendo 44% do
 *        lucro do item.
 */
public record CashbackMarginImpactItem(String sku, String name, BigDecimal marginPercent,
        BigDecimal cashbackPercent, BigDecimal marginShareConsumed) {
}
