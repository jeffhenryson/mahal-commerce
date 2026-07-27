/**
 * Domínio <b>estoque</b>.
 *
 * <p>Grade de produtos com variações (SKU pai e filhos), atributos (sabor, tamanho,
 * cor), multi-depósito (loja física vs. e-commerce) e movimentações de estoque.</p>
 *
 * <p><b>Status:</b> operacional. {@code Product}, {@code ProductVariant},
 * {@code ProductAttribute}, {@code Warehouse}, {@code StockBalance}, {@code StockMovement},
 * {@code ReorderPoint} e {@code ReorderAlert} estão implementados, todos como {@code record}
 * imutável com invariantes no compact constructor e o par {@code create()} (entidade nova) /
 * {@code of()} (reconstituição a partir da persistência).</p>
 *
 * <p>Modelos ainda previstos: lote/validade (EST-F008), inventário/contagem (EST-F006),
 * transferência entre depósitos (EST-F012) e reserva de estoque para checkout (EST-F013).</p>
 */
package com.cernecommerce.core.domain.model.estoque;
