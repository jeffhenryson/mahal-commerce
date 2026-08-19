package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.ReorderAlertCounts;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;

import java.util.Optional;

/**
 * Port de saída para persistência de pontos de reposição de estoque por SKU/depósito.
 */
public interface ReorderPointRepository {

    Optional<ReorderPoint> findBySkuAndWarehouseId(String sku, Long warehouseId);

    /** Pontos de reposição configurados num depósito, paginados, ordenados por SKU. */
    PageResult<ReorderPoint> findByWarehouseId(Long warehouseId, int page, int size);

    ReorderPoint save(ReorderPoint reorderPoint);

    /**
     * Remove o ponto de reposição de um SKU num depósito, se existir. Idempotente — não lança se
     * não havia nenhum configurado (item 4 do pedido do frontend: desmarcar "estoque mínimo
     * próprio" precisa apagar de verdade, não só parar de enviar o valor).
     */
    void deleteBySkuAndWarehouseId(String sku, Long warehouseId);

    /**
     * Contagem de pares SKU/depósito em alerta, por severidade ({@link ReorderPoint#severityFor}),
     * em todos os depósitos — ver {@link com.cernecommerce.core.domain.model.estoque.EstoqueSummary}.
     */
    ReorderAlertCounts countAlerts();
}
