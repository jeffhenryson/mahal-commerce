package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SupplierEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class SupplierRepositoryImpl implements SupplierRepository {

    private final SupplierJpaRepository supplierJpaRepository;

    public SupplierRepositoryImpl(SupplierJpaRepository supplierJpaRepository) {
        this.supplierJpaRepository = supplierJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Supplier> findAll(int page, int size) {
        Page<SupplierEntity> result = supplierJpaRepository.findAll(PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findByTaxId(String taxId) {
        return supplierJpaRepository.findByTaxId(taxId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findById(Long id) {
        return supplierJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Supplier save(Supplier supplier) {
        SupplierEntity entity = new SupplierEntity(supplier.id(), supplier.legalName(), supplier.taxId(),
                supplier.email(), supplier.active());
        return toDomain(supplierJpaRepository.save(entity));
    }

    private Supplier toDomain(SupplierEntity e) {
        return new Supplier(e.getId(), e.getLegalName(), e.getTaxId(), e.getEmail(), e.isActive());
    }
}
