package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashRegisterSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CashRegisterSessionJpaRepository extends JpaRepository<CashRegisterSessionEntity, Long>,
        JpaSpecificationExecutor<CashRegisterSessionEntity> {

    Optional<CashRegisterSessionEntity> findByOperatorAndStatus(String operator, String status);
}
