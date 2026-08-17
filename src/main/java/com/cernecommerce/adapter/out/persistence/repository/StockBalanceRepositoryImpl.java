package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockBalanceEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
@Transactional
public class StockBalanceRepositoryImpl implements StockBalanceRepository {

    private final StockBalanceJpaRepository stockBalanceJpaRepository;

    public StockBalanceRepositoryImpl(StockBalanceJpaRepository stockBalanceJpaRepository) {
        this.stockBalanceJpaRepository = stockBalanceJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockBalance> findBySkuAndWarehouseId(String sku, Long warehouseId) {
        return stockBalanceJpaRepository.findBySkuAndWarehouseId(sku, warehouseId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockBalance> findByWarehouseId(Long warehouseId, int page, int size) {
        Page<StockBalanceEntity> result = stockBalanceJpaRepository
                .findByWarehouseIdOrderBySkuAsc(warehouseId, PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public StockBalance save(StockBalance stockBalance) {
        StockBalanceEntity entity = new StockBalanceEntity();
        entity.setId(stockBalance.id());
        entity.setSku(stockBalance.sku());
        entity.setWarehouseId(stockBalance.warehouseId());
        entity.setQuantity(stockBalance.quantity());
        entity.setReservedQuantity(stockBalance.reservedQuantity());
        entity.setAverageCost(stockBalance.averageCost());
        entity.setVersion(stockBalance.version());
        return toDomain(stockBalanceJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumInventoryValueAtCost() {
        return stockBalanceJpaRepository.sumInventoryValueAtCost();
    }

    private StockBalance toDomain(StockBalanceEntity e) {
        return StockBalance.of(e.getId(), e.getSku(), e.getWarehouseId(), e.getQuantity(),
                e.getReservedQuantity(), e.getAverageCost(), e.getVersion());
    }
}
