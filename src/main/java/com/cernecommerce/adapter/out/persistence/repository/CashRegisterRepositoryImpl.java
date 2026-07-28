package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashRegisterSessionEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
@Transactional
public class CashRegisterRepositoryImpl implements CashRegisterRepository {

    private final CashRegisterSessionJpaRepository cashRegisterSessionJpaRepository;

    public CashRegisterRepositoryImpl(CashRegisterSessionJpaRepository cashRegisterSessionJpaRepository) {
        this.cashRegisterSessionJpaRepository = cashRegisterSessionJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> findAll(int page, int size) {
        Page<CashRegisterSessionEntity> result = cashRegisterSessionJpaRepository.findAll(PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegisterSession> findOpenByOperator(String operator) {
        return cashRegisterSessionJpaRepository
                .findByOperatorAndStatus(operator, CashRegisterSession.Status.OPEN.name())
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashRegisterSession> findById(Long id) {
        return cashRegisterSessionJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public CashRegisterSession save(CashRegisterSession session) {
        CashRegisterSessionEntity entity = session.id() == null
                ? new CashRegisterSessionEntity()
                : cashRegisterSessionJpaRepository.findById(session.id())
                        .orElseGet(CashRegisterSessionEntity::new);
        entity.setId(session.id());
        entity.setOperator(session.operator());
        entity.setOpenedAt(session.openedAt());
        entity.setOpeningAmount(session.openingAmount());
        entity.setWarehouseCode(session.warehouseCode());
        entity.setClosedAt(session.closedAt());
        entity.setClosedBy(session.closedBy());
        entity.setExpectedAmount(session.expectedAmount());
        entity.setCountedAmount(session.countedAmount());
        entity.setDifferenceAmount(session.differenceAmount());
        entity.setStatus(session.status().name());
        return toDomain(cashRegisterSessionJpaRepository.save(entity));
    }

    private CashRegisterSession toDomain(CashRegisterSessionEntity e) {
        return CashRegisterSession.of(e.getId(), e.getOperator(), e.getOpenedAt(), e.getOpeningAmount(),
                e.getWarehouseCode(), e.getClosedAt(), e.getClosedBy(), e.getExpectedAmount(),
                e.getCountedAmount(), e.getDifferenceAmount(),
                CashRegisterSession.Status.valueOf(e.getStatus()));
    }
}
