/**
 * Domínio <b>estoque</b>.
 *
 * <p>Grade de produtos com variações (SKU pai e filhos), atributos (sabor, tamanho,
 * cor), multi-depósito (loja física vs. e-commerce) e movimentações de estoque —
 * inclusive entrada por XML de NF-e.</p>
 *
 * <p><b>Status:</b> esqueleto. Modelos previstos (TODO): {@code ProductVariant}
 * (SKU filho), {@code ProductAttribute} (sabor/tamanho/cor), {@code Warehouse}
 * (depósito), {@code StockBalance} (saldo por depósito), {@code StockMovement}
 * (movimentação, origem NF-e).</p>
 */
package com.cernecommerce.core.domain.model.estoque;
