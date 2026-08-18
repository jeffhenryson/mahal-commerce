package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaEntity;
import com.cernecommerce.adapter.out.persistence.entity.ComandaItemEntity;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class ComandaRepositoryImpl implements ComandaRepository {

    private final ComandaJpaRepository comandaJpaRepository;

    public ComandaRepositoryImpl(ComandaJpaRepository comandaJpaRepository) {
        this.comandaJpaRepository = comandaJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Comanda> findById(Long id) {
        return comandaJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Comanda> findOpenBySessionId(Long sessionId) {
        return comandaJpaRepository
                .findBySessionIdAndStatusOrderByIdDesc(sessionId, ComandaStatus.ABERTA.name())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Comanda save(Comanda comanda) {
        ComandaEntity entity = comanda.id() == null
                ? new ComandaEntity()
                : comandaJpaRepository.findById(comanda.id()).orElseGet(ComandaEntity::new);
        entity.setId(comanda.id());
        entity.setSessionId(comanda.sessionId());
        entity.setWarehouseCode(comanda.warehouseCode());
        entity.setTableOrCustomerLabel(comanda.tableOrCustomerLabel());
        entity.setStatus(comanda.status().name());
        entity.setOrderId(comanda.orderId());
        entity.setOpenedBy(comanda.openedBy());
        entity.setOpenedAt(comanda.openedAt());
        entity.setClosedAt(comanda.closedAt());

        // Itens só crescem enquanto ABERTA (append-only via addItem) — reescrever a lista inteira
        // a cada save é seguro e simples, mesmo padrão de OrderRepositoryImpl.
        entity.getItems().clear();
        for (ComandaItem item : comanda.items()) {
            ComandaItemEntity itemEntity = new ComandaItemEntity();
            itemEntity.setComanda(entity);
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setCostPrice(item.costPrice());
            itemEntity.setProductName(item.productName());
            itemEntity.setAddedAt(item.addedAt());
            entity.getItems().add(itemEntity);
        }
        return toDomain(comandaJpaRepository.save(entity));
    }

    private Comanda toDomain(ComandaEntity e) {
        return Comanda.of(e.getId(), e.getSessionId(), e.getWarehouseCode(), e.getTableOrCustomerLabel(),
                ComandaStatus.valueOf(e.getStatus()), e.getItems().stream().map(this::toDomain).toList(),
                e.getOrderId(), e.getOpenedBy(), e.getOpenedAt(), e.getClosedAt());
    }

    private ComandaItem toDomain(ComandaItemEntity e) {
        return ComandaItem.of(e.getId(), e.getSku(), e.getQuantity(), e.getUnitPrice(), e.getCostPrice(),
                e.getProductName(), e.getAddedAt());
    }
}
