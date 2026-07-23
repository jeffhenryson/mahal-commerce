package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReorderPointEntity;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class ReorderPointRepositoryImpl implements ReorderPointRepository {

    private final ReorderPointJpaRepository reorderPointJpaRepository;

    public ReorderPointRepositoryImpl(ReorderPointJpaRepository reorderPointJpaRepository) {
        this.reorderPointJpaRepository = reorderPointJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReorderPoint> findBySkuAndWarehouseId(String sku, Long warehouseId) {
        return reorderPointJpaRepository.findBySkuAndWarehouseId(sku, warehouseId).map(this::toDomain);
    }

    @Override
    public ReorderPoint save(ReorderPoint reorderPoint) {
        ReorderPointEntity entity = new ReorderPointEntity();
        entity.setId(reorderPoint.id());
        entity.setSku(reorderPoint.sku());
        entity.setWarehouseId(reorderPoint.warehouseId());
        entity.setMinQuantity(reorderPoint.minQuantity());
        return toDomain(reorderPointJpaRepository.save(entity));
    }

    private ReorderPoint toDomain(ReorderPointEntity e) {
        return ReorderPoint.of(e.getId(), e.getSku(), e.getWarehouseId(), e.getMinQuantity());
    }
}
