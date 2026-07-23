package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReorderPointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReorderPointJpaRepository extends JpaRepository<ReorderPointEntity, Long> {

    Optional<ReorderPointEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);
}
