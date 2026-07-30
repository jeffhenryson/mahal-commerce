package com.cernecommerce.core.domain.model.cashback;

/**
 * Abrangência de uma {@link CashbackRate} (CRM-F003).
 *
 * <p>A cadeia de resolução é {@code SKU → CATEGORY → GLOBAL}: a regra ativa mais específica
 * vence. Uma coluna {@code cashback_percent} em {@code product} seria mais simples, mas não
 * expressa "categoria X tem taxa Y" — e {@code Product.category} é texto livre, sem tabela
 * própria, então a regra por categoria precisaria ser chaveada pelo texto de qualquer jeito.
 * Uma tabela uniforme para as três abrangências custa o mesmo e evita três mecanismos
 * diferentes.</p>
 */
public enum CashbackScope {
    GLOBAL,
    CATEGORY,
    SKU
}
