package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockLotEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockLotJpaRepository extends JpaRepository<StockLotEntity, Long> {

    Optional<StockLotEntity> findBySkuAndWarehouseIdAndLotCode(String sku, Long warehouseId, String lotCode);

    /** Ordem FEFO: o que vence primeiro sai primeiro. Desempate por {@code id} para ordem estável. */
    List<StockLotEntity> findBySkuAndWarehouseIdOrderByExpiryDateAscIdAsc(String sku, Long warehouseId);

    /**
     * Lotes com saldo (quantity > 0) ainda não alertados cujo vencimento já chegou em
     * {@code cutoff} — inclui lote já vencido, não só o que está se aproximando.
     */
    @Query("""
            SELECT l FROM StockLotEntity l
            WHERE l.quantity > 0 AND l.alertedAt IS NULL AND l.expiryDate <= :cutoff
            ORDER BY l.expiryDate ASC, l.id ASC
            """)
    List<StockLotEntity> findExpiringSoon(@Param("cutoff") LocalDate cutoff, Pageable pageable);
}
