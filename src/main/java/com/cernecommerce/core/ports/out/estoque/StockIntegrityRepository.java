package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;

/**
 * Port de saída para os diagnósticos de integridade referencial do estoque (EST-C011, EST-C013).
 *
 * <p>É um port separado dos demais de propósito: as consultas atravessam tabelas de mais de um
 * agregado e não pertencem a nenhum deles isoladamente.</p>
 *
 * <p><b>Somente leitura.</b> A correção de cada divergência é decisão humana, e por isso não
 * existe aqui nenhuma operação de escrita.</p>
 */
public interface StockIntegrityRepository {

    /**
     * Pares SKU/depósito com saldo, movimentação ou ponto de reposição gravados cujo SKU não
     * existe no catálogo (nem como SKU pai, nem como SKU de variação). Ordenado por
     * {@code sku, warehouseCode} — chave única, para a paginação ser estável.
     */
    PageResult<OrphanSku> findOrphanSkus(int page, int size);

    /**
     * Pares SKU/depósito cujo {@code stock_balance.reserved_quantity} diverge da soma das
     * reservas {@code ACTIVE} em {@code stock_reservation} (EST-C013). Ordenado por
     * {@code sku, warehouseCode} — chave única, para a paginação ser estável.
     */
    PageResult<ReservationIntegrityMismatch> findReservationMismatches(int page, int size);
}
