package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockBalanceEntity;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    public StockBalance save(StockBalance stockBalance) {
        StockBalanceEntity entity = new StockBalanceEntity();
        entity.setId(stockBalance.id());
        entity.setSku(stockBalance.sku());
        entity.setWarehouseId(stockBalance.warehouseId());
        entity.setQuantity(stockBalance.quantity());
        entity.setVersion(stockBalance.version());
        return toDomain(stockBalanceJpaRepository.save(entity));
    }

    private StockBalance toDomain(StockBalanceEntity e) {
        return StockBalance.of(e.getId(), e.getSku(), e.getWarehouseId(), e.getQuantity(), e.getVersion());
    }
}
