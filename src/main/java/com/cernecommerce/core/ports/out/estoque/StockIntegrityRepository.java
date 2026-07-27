package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;

/**
 * Port de saída para o diagnóstico de integridade referencial do estoque (EST-C011).
 *
 * <p>É um port separado dos demais de propósito: a consulta atravessa as cinco tabelas do
 * módulo ({@code stock_balance}, {@code stock_movement}, {@code stock_reorder_point},
 * {@code product}, {@code product_variant}) e não pertence ao agregado de nenhuma delas.</p>
 *
 * <p><b>Somente leitura.</b> A limpeza dos órfãos é decisão humana — cadastrar o produto que
 * falta ou expurgar a linha —, e por isso não existe aqui nenhuma operação de escrita.</p>
 */
public interface StockIntegrityRepository {

    /**
     * Pares SKU/depósito com saldo, movimentação ou ponto de reposição gravados cujo SKU não
     * existe no catálogo (nem como SKU pai, nem como SKU de variação). Ordenado por
     * {@code sku, warehouseCode} — chave única, para a paginação ser estável.
     */
    PageResult<OrphanSku> findOrphanSkus(int page, int size);
}
