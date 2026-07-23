package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SaleEntity;
import com.cernecommerce.adapter.out.persistence.entity.SaleItemEntity;
import com.cernecommerce.core.domain.model.pdv.Sale;
import com.cernecommerce.core.domain.model.pdv.SaleItem;
import com.cernecommerce.core.ports.out.pdv.SaleRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class SaleRepositoryImpl implements SaleRepository {

    private final SaleJpaRepository saleJpaRepository;

    public SaleRepositoryImpl(SaleJpaRepository saleJpaRepository) {
        this.saleJpaRepository = saleJpaRepository;
    }

    @Override
    public Sale save(Sale sale) {
        SaleEntity entity = new SaleEntity();
        entity.setId(sale.id());
        entity.setSessionId(sale.sessionId());
        entity.setWarehouseCode(sale.warehouseCode());
        entity.setSoldAt(sale.soldAt());
        entity.setTotalAmount(sale.totalAmount());
        for (SaleItem item : sale.items()) {
            SaleItemEntity itemEntity = new SaleItemEntity();
            itemEntity.setSale(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            entity.getItems().add(itemEntity);
        }
        return toDomain(saleJpaRepository.save(entity));
    }

    private Sale toDomain(SaleEntity e) {
        return Sale.of(e.getId(), e.getSessionId(), e.getWarehouseCode(),
                e.getItems().stream().map(this::toDomain).toList(), e.getTotalAmount(), e.getSoldAt());
    }

    private SaleItem toDomain(SaleItemEntity e) {
        return new SaleItem(e.getSku(), e.getQuantity(), e.getUnitPrice());
    }
}
