package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashRegisterSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CashRegisterSessionJpaRepository extends JpaRepository<CashRegisterSessionEntity, Long> {

    Optional<CashRegisterSessionEntity> findByOperatorAndStatus(String operator, String status);
}
