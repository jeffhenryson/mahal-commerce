package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.GoodsReceiptEntity;
import com.cernecommerce.adapter.out.persistence.entity.GoodsReceiptItemEntity;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.ports.out.compras.GoodsReceiptRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class GoodsReceiptRepositoryImpl implements GoodsReceiptRepository {

    private final GoodsReceiptJpaRepository goodsReceiptJpaRepository;

    public GoodsReceiptRepositoryImpl(GoodsReceiptJpaRepository goodsReceiptJpaRepository) {
        this.goodsReceiptJpaRepository = goodsReceiptJpaRepository;
    }

    @Override
    public GoodsReceipt save(GoodsReceipt receipt) {
        GoodsReceiptEntity entity = new GoodsReceiptEntity();
        entity.setId(receipt.id());
        entity.setSupplierId(receipt.supplierId());
        entity.setWarehouseCode(receipt.warehouseCode());
        entity.setUsername(receipt.username());
        entity.setReceivedAt(receipt.receivedAt());
        for (GoodsReceiptItem item : receipt.items()) {
            GoodsReceiptItemEntity itemEntity = new GoodsReceiptItemEntity();
            itemEntity.setGoodsReceipt(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setLotCode(item.lotCode());
            itemEntity.setExpiryDate(item.expiryDate());
            entity.getItems().add(itemEntity);
        }
        return toDomain(goodsReceiptJpaRepository.save(entity));
    }

    private GoodsReceipt toDomain(GoodsReceiptEntity e) {
        return GoodsReceipt.of(e.getId(), e.getSupplierId(), e.getWarehouseCode(),
                e.getItems().stream().map(this::toDomain).toList(), e.getUsername(), e.getReceivedAt());
    }

    private GoodsReceiptItem toDomain(GoodsReceiptItemEntity e) {
        return new GoodsReceiptItem(e.getSku(), e.getQuantity(), e.getLotCode(), e.getExpiryDate());
    }
}
