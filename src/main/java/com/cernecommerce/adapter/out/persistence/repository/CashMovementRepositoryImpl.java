package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashMovementEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
    public PageResult<CashMovement> findBySessionId(Long sessionId, int page, int size) {
        Page<CashMovementEntity> result = cashMovementJpaRepository
                .findBySessionIdOrderByIdAsc(sessionId, PageRequest.of(page, size));
        // Sem ID-first aqui, ao contrário da comanda: CashMovementEntity não tem coleção filha,
        // então o bug de LIMIT/OFFSET junto de JOIN FETCH não existe neste caminho.
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
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
