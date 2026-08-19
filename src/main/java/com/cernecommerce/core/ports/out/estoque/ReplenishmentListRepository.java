package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência da lista de reposição por depósito (item 1 do pedido do
 * frontend).
 */
public interface ReplenishmentListRepository {

    /** Upsert por {@code (sku, warehouseId)} — anotar de novo substitui o item, não duplica. */
    ReplenishmentListItem save(ReplenishmentListItem item);

    Optional<ReplenishmentListItem> findBySkuAndWarehouseId(String sku, Long warehouseId);

    /** Itens de um depósito, mais recentemente anotados primeiro. */
    List<ReplenishmentListItem> findByWarehouseId(Long warehouseId);

    void deleteBySkuAndWarehouseId(String sku, Long warehouseId);

    /** Limpa a lista inteira de um depósito. */
    void deleteByWarehouseId(Long warehouseId);
}
