package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StageTransitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StageTransitionJpaRepository extends JpaRepository<StageTransitionEntity, Long> {

    List<StageTransitionEntity> findByCustomerIdOrderByTransicionadoEmDesc(Long customerId);
}
