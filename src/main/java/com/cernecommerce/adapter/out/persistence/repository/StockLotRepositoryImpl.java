package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockLotEntity;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.ports.out.estoque.StockLotRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class StockLotRepositoryImpl implements StockLotRepository {

    private final StockLotJpaRepository stockLotJpaRepository;

    public StockLotRepositoryImpl(StockLotJpaRepository stockLotJpaRepository) {
        this.stockLotJpaRepository = stockLotJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockLot> findBySkuAndWarehouseIdAndLotCode(String sku, Long warehouseId, String lotCode) {
        return stockLotJpaRepository.findBySkuAndWarehouseIdAndLotCode(sku, warehouseId, lotCode).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLot> findBySkuAndWarehouseId(String sku, Long warehouseId) {
        return stockLotJpaRepository.findBySkuAndWarehouseIdOrderByExpiryDateAscIdAsc(sku, warehouseId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockLot> findById(Long id) {
        return stockLotJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public StockLot save(StockLot stockLot) {
        StockLotEntity entity = new StockLotEntity();
        entity.setId(stockLot.id());
        entity.setSku(stockLot.sku());
        entity.setWarehouseId(stockLot.warehouseId());
        entity.setLotCode(stockLot.lotCode());
        entity.setExpiryDate(stockLot.expiryDate());
        entity.setQuantity(stockLot.quantity());
        entity.setAlertedAt(stockLot.alertedAt());
        entity.setVersion(stockLot.version());
        return toDomain(stockLotJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLot> findExpiringSoon(LocalDate cutoff, int batchSize) {
        return stockLotJpaRepository.findExpiringSoon(cutoff, PageRequest.of(0, batchSize))
                .stream().map(this::toDomain).toList();
    }

    private StockLot toDomain(StockLotEntity e) {
        return StockLot.of(e.getId(), e.getSku(), e.getWarehouseId(), e.getLotCode(), e.getExpiryDate(),
                e.getQuantity(), e.getAlertedAt(), e.getVersion());
    }
}
