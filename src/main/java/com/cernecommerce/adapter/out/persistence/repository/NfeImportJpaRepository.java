package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.NfeImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NfeImportJpaRepository extends JpaRepository<NfeImportEntity, Long> {
}
