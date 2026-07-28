/**
 * Domínio <b>pedido</b> — o documento de venda, comum a todos os canais (PDV-F003).
 *
 * <p>Existe um {@link com.cernecommerce.core.domain.model.pedido.Order} só, discriminado por
 * {@link com.cernecommerce.core.domain.model.pedido.SalesChannel}, porque tudo que consome venda
 * consome "vendas, independente de canal": o extrato do cliente, o ledger de cashback, a devolução,
 * o faturamento, o documento fiscal e o relatório de margem. Duas tabelas fariam cada um desses
 * consumidores pagar um {@code UNION} ou duplicar lógica — e nenhuma interface em Java ajuda um
 * {@code SELECT}.</p>
 *
 * <p>Este pacote <b>substitui</b> {@code core.domain.model.pdv.Sale} e {@code SaleItem}, que não
 * coexistem com ele. O que permanece em {@code pdv} é o que é exclusivo do balcão: a sessão de
 * caixa e seus movimentos.</p>
 *
 * <p>Fora do escopo por desenho: sessão de caixa, formas de pagamento, endereço e frete moram em
 * tabelas próprias, populadas só pelo canal que as tem — é o que evita o pedido de 40 colunas em
 * que metade é sempre nula.</p>
 */
package com.cernecommerce.core.domain.model.pedido;
