package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Resumo pré-calculado do catálogo de estoque — números agregados que várias telas do admin
 * precisavam recalcular do zero baixando o catálogo inteiro (badge de alertas na shell do
 * Estoque, tela de Alertas de Reposição, KPIs do topo do Catálogo de Produtos, painel de Estoque
 * do Dashboard). Ver {@code EstoqueUseCase.getSummary}.
 *
 * @param totalProdutos quantidade de SKUs pai no catálogo, ativos ou não — KPI de tamanho do
 *        catálogo, distinto de "vendável agora".
 * @param totalVariantes quantidade de variações (SKU filho) em todo o catálogo.
 * @param valorEstoqueCusto soma de {@code quantity * costPrice efetivo} de todo saldo em todos os
 *        depósitos. Saldo sem preço conhecido contribui zero, não envenena a soma.
 * @param alertasCriticos quantidade de pares SKU/depósito em {@link AlertSeverity#CRITICO}.
 * @param alertasAtencao quantidade de pares SKU/depósito em {@link AlertSeverity#ATENCAO}.
 * @param categoriaComMaisProdutos categoria com mais produtos vinculados, ou {@code null} se
 *        nenhum produto está vinculado a uma categoria.
 */
public record EstoqueSummary(long totalProdutos, long totalVariantes, BigDecimal valorEstoqueCusto,
        long alertasCriticos, long alertasAtencao, CategoryProductCount categoriaComMaisProdutos) {
}
