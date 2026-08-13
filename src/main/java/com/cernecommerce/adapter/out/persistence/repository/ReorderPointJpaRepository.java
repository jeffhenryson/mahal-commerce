package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReorderPointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReorderPointJpaRepository extends JpaRepository<ReorderPointEntity, Long> {

    Optional<ReorderPointEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);

    Page<ReorderPointEntity> findByWarehouseIdOrderBySkuAsc(Long warehouseId, Pageable pageable);

    // EST-F022 — GET /estoque/summary. Replica em SQL a mesma regra de
    // ReorderPoint.severityFor: CRÍTICO é saldo <= 0 OU <= minQuantity * ReorderPoint
    // .CRITICAL_THRESHOLD_FACTOR (0.5); ATENÇÃO é abaixo do mínimo mas acima do limiar crítico.
    // List<Object[]> (mesmo idioma de findCategoryWithMostProductsRaw) e não uma constructor
    // expression: SUM sem linha nenhuma devolve NULL, e o adapter converte para 0 — uma
    // constructor expression para (long, long) quebraria nesse caso.
    @Query("""
            SELECT
              SUM(CASE WHEN sb.quantity <= 0 OR sb.quantity <= (rp.minQuantity * 0.5) THEN 1L ELSE 0L END),
              SUM(CASE WHEN sb.quantity > 0 AND sb.quantity < rp.minQuantity
                            AND sb.quantity > (rp.minQuantity * 0.5) THEN 1L ELSE 0L END)
            FROM ReorderPointEntity rp
            JOIN StockBalanceEntity sb ON sb.sku = rp.sku AND sb.warehouseId = rp.warehouseId
            """)
    List<Object[]> countAlertsRaw();
}
