package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OpenPackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OpenPackageJpaRepository extends JpaRepository<OpenPackageEntity, Long> {

    Optional<OpenPackageEntity> findBySkuAndWarehouseIdAndClosedAtIsNull(String sku, Long warehouseId);

    List<OpenPackageEntity> findByWarehouseIdAndClosedAtIsNullOrderBySkuAsc(Long warehouseId);
}
