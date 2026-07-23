package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SaleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleJpaRepository extends JpaRepository<SaleEntity, Long> {
}
