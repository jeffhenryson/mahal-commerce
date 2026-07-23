package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {

    Page<StockMovementEntity> findBySkuAndWarehouseIdOrderByCreatedAtDesc(String sku, Long warehouseId,
            Pageable pageable);
}
