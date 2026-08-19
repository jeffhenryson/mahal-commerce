package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReplenishmentListItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReplenishmentListItemJpaRepository extends JpaRepository<ReplenishmentListItemEntity, Long> {

    Optional<ReplenishmentListItemEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);

    List<ReplenishmentListItemEntity> findByWarehouseIdOrderByCreatedAtDesc(Long warehouseId);

    void deleteBySkuAndWarehouseId(String sku, Long warehouseId);

    void deleteByWarehouseId(Long warehouseId);
}
