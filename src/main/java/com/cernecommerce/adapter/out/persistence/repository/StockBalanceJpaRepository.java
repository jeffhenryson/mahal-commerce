package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockBalanceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockBalanceJpaRepository extends JpaRepository<StockBalanceEntity, Long> {

    Optional<StockBalanceEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);

    Page<StockBalanceEntity> findByWarehouseIdOrderBySkuAsc(Long warehouseId, Pageable pageable);
}
