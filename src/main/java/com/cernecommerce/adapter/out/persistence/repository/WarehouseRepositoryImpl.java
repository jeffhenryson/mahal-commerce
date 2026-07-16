package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.WarehouseEntity;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class WarehouseRepositoryImpl implements WarehouseRepository {

    private final WarehouseJpaRepository warehouseJpaRepository;

    public WarehouseRepositoryImpl(WarehouseJpaRepository warehouseJpaRepository) {
        this.warehouseJpaRepository = warehouseJpaRepository;
    }

    @Override
    public Warehouse save(Warehouse warehouse) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(warehouse.id());
        entity.setCode(warehouse.code());
        entity.setName(warehouse.name());
        entity.setType(warehouse.type());
        entity.setActive(warehouse.active());
        return toDomain(warehouseJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Warehouse> findByCode(String code) {
        return warehouseJpaRepository.findByCode(code).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Warehouse> findAll() {
        return warehouseJpaRepository.findAllOrderById().stream().map(this::toDomain).toList();
    }

    private Warehouse toDomain(WarehouseEntity e) {
        return Warehouse.of(e.getId(), e.getCode(), e.getName(), e.getType(), e.isActive());
    }
}
