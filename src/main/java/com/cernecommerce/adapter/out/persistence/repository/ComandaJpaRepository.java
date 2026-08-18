package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComandaJpaRepository extends JpaRepository<ComandaEntity, Long> {

    List<ComandaEntity> findBySessionIdAndStatusOrderByIdDesc(Long sessionId, String status);
}
