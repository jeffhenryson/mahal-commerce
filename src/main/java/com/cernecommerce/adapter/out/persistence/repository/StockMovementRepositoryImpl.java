package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class StockMovementRepositoryImpl implements StockMovementRepository {

    private final StockMovementJpaRepository stockMovementJpaRepository;

    public StockMovementRepositoryImpl(StockMovementJpaRepository stockMovementJpaRepository) {
        this.stockMovementJpaRepository = stockMovementJpaRepository;
    }

    @Override
    public StockMovement save(StockMovement movement) {
        StockMovementEntity entity = new StockMovementEntity();
        entity.setId(movement.id());
        entity.setSku(movement.sku());
        entity.setWarehouseId(movement.warehouseId());
        entity.setType(movement.type().name());
        entity.setQuantity(movement.quantity());
        entity.setReason(movement.reason());
        entity.setUsername(movement.username());
        entity.setCreatedAt(movement.createdAt());
        entity.setLotCode(movement.lotCode());
        return toDomain(stockMovementJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> findBySkuAndWarehouseId(String sku, Long warehouseId, int page, int size) {
        Page<StockMovementEntity> result = stockMovementJpaRepository
                .findBySkuAndWarehouseIdOrderByCreatedAtDescIdDesc(sku, warehouseId, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private StockMovement toDomain(StockMovementEntity e) {
        return StockMovement.of(e.getId(), e.getSku(), e.getWarehouseId(), MovementType.valueOf(e.getType()),
                e.getQuantity(), e.getReason(), e.getUsername(), e.getCreatedAt(), e.getLotCode());
    }
}
