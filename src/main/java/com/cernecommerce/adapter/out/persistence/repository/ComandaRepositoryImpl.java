package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaEntity;
import com.cernecommerce.adapter.out.persistence.entity.ComandaItemEntity;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        entity.setCustomerId(comanda.customerId());
        entity.setOrderId(comanda.orderId());
        entity.setOpenedBy(comanda.openedBy());
        entity.setOpenedAt(comanda.openedAt());
        entity.setClosedAt(comanda.closedAt());

        // Linha já persistida é ATUALIZADA no lugar, nunca recriada. O apaga-e-reinsere que estava
        // aqui era inofensivo em PDV-F009 e deixou de ser na V114: linked_item_id aponta para o id
        // de outra linha desta mesma comanda, então trocar os ids a cada addItem faria a primeira
        // TROCA de um open rosh apontar para uma linha recém-deletada — violação de FK. E o
        // linkedItemId que o cliente recebeu tem que continuar valendo no lançamento seguinte.
        Map<Long, ComandaItem> incoming = comanda.items().stream()
                .filter(item -> item.id() != null)
                .collect(Collectors.toMap(ComandaItem::id, Function.identity()));
        // Nada remove item de comanda hoje; o orphanRemoval fica correto se o endpoint aparecer.
        entity.getItems().removeIf(e -> e.getId() != null && !incoming.containsKey(e.getId()));

        Map<Long, ComandaItemEntity> persisted = entity.getItems().stream()
                .filter(e -> e.getId() != null)
                .collect(Collectors.toMap(ComandaItemEntity::getId, Function.identity()));
        for (ComandaItem item : comanda.items()) {
            ComandaItemEntity itemEntity = item.id() == null ? null : persisted.get(item.id());
            if (itemEntity == null) {
                itemEntity = new ComandaItemEntity();
                itemEntity.setComanda(entity);
                entity.getItems().add(itemEntity);
            }
            itemEntity.setSku(item.sku());
            itemEntity.setQuantity(item.quantity());
            itemEntity.setUnitPrice(item.unitPrice());
            itemEntity.setCostPrice(item.costPrice());
            itemEntity.setProductName(item.productName());
            itemEntity.setAddedAt(item.addedAt());
            itemEntity.setMode(item.mode().name());
            itemEntity.setCourtesy(item.courtesy());
            itemEntity.setLinkedItemId(item.linkedItemId());
        }
        return toDomain(comandaJpaRepository.save(entity));
    }

    private Comanda toDomain(ComandaEntity e) {
        return Comanda.of(e.getId(), e.getSessionId(), e.getWarehouseCode(), e.getTableOrCustomerLabel(),
                e.getCustomerId(), ComandaStatus.valueOf(e.getStatus()),
                e.getItems().stream().map(this::toDomain).toList(),
                e.getOrderId(), e.getOpenedBy(), e.getOpenedAt(), e.getClosedAt());
    }

    private ComandaItem toDomain(ComandaItemEntity e) {
        return ComandaItem.of(e.getId(), e.getSku(), e.getQuantity(), e.getUnitPrice(), e.getCostPrice(),
                e.getProductName(), e.getAddedAt(),
                // Dado legado (linha anterior a PDV-F010) lê como NORMAL — o DEFAULT da migration
                // cobre as linhas já gravadas, e este null-check cobre carga direta.
                e.getMode() == null ? ConsumptionMode.NORMAL : ConsumptionMode.valueOf(e.getMode()),
                e.isCourtesy(), e.getLinkedItemId());
    }
}
