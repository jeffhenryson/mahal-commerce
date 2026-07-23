package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SupplierEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierJpaRepository extends JpaRepository<SupplierEntity, Long> {

    Optional<SupplierEntity> findByTaxId(String taxId);
}
