package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReplenishmentListItemEntity;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.ports.out.estoque.ReplenishmentListRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class ReplenishmentListRepositoryImpl implements ReplenishmentListRepository {

    private final ReplenishmentListItemJpaRepository jpaRepository;

    public ReplenishmentListRepositoryImpl(ReplenishmentListItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ReplenishmentListItem save(ReplenishmentListItem item) {
        ReplenishmentListItemEntity entity = jpaRepository.findBySkuAndWarehouseId(item.sku(), item.warehouseId())
                .orElseGet(ReplenishmentListItemEntity::new);
        entity.setSku(item.sku());
        entity.setWarehouseId(item.warehouseId());
        entity.setProductNameSnapshot(item.productNameSnapshot());
        entity.setCategorySnapshot(item.categorySnapshot());
        entity.setBrandSnapshot(item.brandSnapshot());
        entity.setUnitSnapshot(item.unitSnapshot() == null ? null : item.unitSnapshot().name());
        entity.setCurrentStockSnapshot(item.currentStockSnapshot());
        entity.setMinStockSnapshot(item.minStockSnapshot());
        entity.setSuggestedQuantitySnapshot(item.suggestedQuantitySnapshot());
        entity.setQuantity(item.quantity());
        entity.setUnitCostSnapshot(item.unitCostSnapshot());
        entity.setPreviousPurchaseQuantitySnapshot(item.previousPurchaseQuantitySnapshot());
        entity.setPreviousPurchaseUnitCostSnapshot(item.previousPurchaseUnitCostSnapshot());
        entity.setPreviousPurchasedAtSnapshot(item.previousPurchasedAtSnapshot());
        entity.setNote(item.note());
        entity.setCreatedAt(item.createdAt());
        entity.setCreatedBy(item.createdBy());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReplenishmentListItem> findBySkuAndWarehouseId(String sku, Long warehouseId) {
        return jpaRepository.findBySkuAndWarehouseId(sku, warehouseId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReplenishmentListItem> findByWarehouseId(Long warehouseId) {
        return jpaRepository.findByWarehouseIdOrderByCreatedAtDesc(warehouseId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteBySkuAndWarehouseId(String sku, Long warehouseId) {
        jpaRepository.deleteBySkuAndWarehouseId(sku, warehouseId);
    }

    @Override
    public void deleteByWarehouseId(Long warehouseId) {
        jpaRepository.deleteByWarehouseId(warehouseId);
    }

    private ReplenishmentListItem toDomain(ReplenishmentListItemEntity e) {
        return ReplenishmentListItem.of(e.getId(), e.getSku(), e.getWarehouseId(), e.getProductNameSnapshot(),
                e.getCategorySnapshot(), e.getBrandSnapshot(),
                e.getUnitSnapshot() == null ? null : MeasurementUnit.valueOf(e.getUnitSnapshot()),
                e.getCurrentStockSnapshot(), e.getMinStockSnapshot(), e.getSuggestedQuantitySnapshot(),
                e.getQuantity(), e.getUnitCostSnapshot(), e.getPreviousPurchaseQuantitySnapshot(),
                e.getPreviousPurchaseUnitCostSnapshot(), e.getPreviousPurchasedAtSnapshot(), e.getNote(),
                e.getCreatedAt(), e.getCreatedBy());
    }
}
