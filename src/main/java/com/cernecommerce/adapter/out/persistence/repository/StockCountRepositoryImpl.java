package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockCountEntity;
import com.cernecommerce.adapter.out.persistence.entity.StockCountItemEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class StockCountRepositoryImpl implements StockCountRepository {

    private final StockCountJpaRepository stockCountJpaRepository;

    public StockCountRepositoryImpl(StockCountJpaRepository stockCountJpaRepository) {
        this.stockCountJpaRepository = stockCountJpaRepository;
    }

    @Override
    public StockCount save(StockCount stockCount) {
        StockCountEntity entity = new StockCountEntity();
        entity.setId(stockCount.id());
        entity.setWarehouseId(stockCount.warehouseId());
        entity.setStatus(stockCount.status().name());
        entity.setUsername(stockCount.username());
        entity.setCreatedAt(stockCount.createdAt());
        entity.setClosedAt(stockCount.closedAt());
        for (StockCountItem item : stockCount.items()) {
            StockCountItemEntity itemEntity = new StockCountItemEntity();
            itemEntity.setId(item.id());
            itemEntity.setStockCount(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setCountedQuantity(item.countedQuantity());
            itemEntity.setExpectedQuantity(item.expectedQuantity());
            itemEntity.setDifference(item.difference());
            itemEntity.setLotCode(item.lotCode());
            entity.getItems().add(itemEntity);
        }
        return toDomain(stockCountJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockCount> findById(Long id) {
        return stockCountJpaRepository.findByIdWithItems(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockCount> findOpenByWarehouseId(Long warehouseId) {
        return stockCountJpaRepository
                .findByWarehouseIdAndStatus(warehouseId, StockCountStatus.ABERTA.name())
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockCount> findByWarehouseId(Long warehouseId, int page, int size) {
        Page<Long> idPage = stockCountJpaRepository
                .findIdsByWarehouseId(warehouseId, PageRequest.of(page, size));
        List<StockCount> content = stockCountJpaRepository
                .findAllByIdsWithItems(idPage.getContent()).stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    private StockCount toDomain(StockCountEntity e) {
        List<StockCountItem> items = e.getItems().stream()
                .map(i -> StockCountItem.of(i.getId(), i.getSku(), i.getCountedQuantity(),
                        i.getExpectedQuantity(), i.getDifference(), i.getLotCode()))
                .toList();
        return StockCount.of(e.getId(), e.getWarehouseId(), StockCountStatus.valueOf(e.getStatus()),
                e.getUsername(), e.getCreatedAt(), e.getClosedAt(), items);
    }
}
