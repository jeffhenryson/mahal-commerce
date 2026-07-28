package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashMovementEntity;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Repository
@Transactional
public class CashMovementRepositoryImpl implements CashMovementRepository {

    private final CashMovementJpaRepository cashMovementJpaRepository;

    public CashMovementRepositoryImpl(CashMovementJpaRepository cashMovementJpaRepository) {
        this.cashMovementJpaRepository = cashMovementJpaRepository;
    }

    @Override
    public CashMovement save(CashMovement movement) {
        CashMovementEntity entity = new CashMovementEntity();
        entity.setId(movement.id());
        entity.setSessionId(movement.sessionId());
        entity.setType(movement.type().name());
        entity.setAmount(movement.amount());
        entity.setReason(movement.reason());
        entity.setUsername(movement.username());
        entity.setCreatedAt(movement.createdAt());
        return toDomain(cashMovementJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashMovement> findBySessionId(Long sessionId) {
        return cashMovementJpaRepository.findBySessionIdOrderByIdAsc(sessionId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumSignedAmountBySessionId(Long sessionId) {
        BigDecimal sum = cashMovementJpaRepository.sumSignedAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private CashMovement toDomain(CashMovementEntity e) {
        return CashMovement.of(e.getId(), e.getSessionId(), CashMovementType.valueOf(e.getType()),
                e.getAmount(), e.getReason(), e.getUsername(), e.getCreatedAt());
    }
}
