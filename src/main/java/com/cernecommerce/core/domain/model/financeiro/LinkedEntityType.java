package com.cernecommerce.core.domain.model.financeiro;

/**
 * Tipo da origem opcional de um lançamento de fluxo de caixa (FIN-F004) — vínculo solto, sem FK
 * de banco (ver {@code linked_entity_id} em {@code LedgerEntity}), já que aponta para tabelas
 * diferentes conforme o valor.
 *
 * <p>O contrato do front fala em vínculo com "pedido, compra ou venda de origem", mas venda de
 * balcão e pedido de e-commerce são o mesmo agregado neste backend ({@code sales_order},
 * distinguido por {@code SalesChannel}) — por isso um único {@link #ORDER} cobre os dois lados,
 * e {@link #PURCHASE} cobre a compra ({@code GoodsReceipt}).</p>
 */
public enum LinkedEntityType {
    ORDER,
    PURCHASE
}
