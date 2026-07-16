package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.WarehouseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WarehouseJpaRepository extends JpaRepository<WarehouseEntity, Long> {

    Optional<WarehouseEntity> findByCode(String code);

    @Query("SELECT w FROM WarehouseEntity w ORDER BY w.id")
    List<WarehouseEntity> findAllOrderById();
}
