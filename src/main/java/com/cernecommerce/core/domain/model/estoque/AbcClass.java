package com.cernecommerce.core.domain.model.estoque;

/**
 * Faixa da curva ABC (EST-F011): o quanto um SKU pesa no valor total consumido.
 *
 * <p>Os cortes seguem a convenção de Pareto usada em gestão de estoque — 80% do valor está em A,
 * os 15% seguintes em B, o resto em C. Não é sobre <i>quantidade</i> de itens: a essência que sai
 * pouco mas custa caro pode ser A, e o carvão que sai todo dia mas custa centavos pode ser C. É
 * exatamente essa inversão que o relatório existe para mostrar a quem compra.</p>
 */
public enum AbcClass {

    /** Até 80% do valor acumulado. Poucos SKUs, quase todo o dinheiro — é onde a atenção rende. */
    A,

    /** De 80% a 95%. A faixa intermediária. */
    B,

    /** Os últimos 5% do valor. Muitos SKUs, pouco dinheiro — comprar por conveniência, não por análise. */
    C
}
