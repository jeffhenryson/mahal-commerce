package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashRegisterSessionEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    /**
     * PDV-C013 — {@code PageRequest.of(page, size)} vinha <b>sem {@code Sort}</b>. Paginação sem
     * {@code ORDER BY} não tem ordem determinística: o Postgres pode devolver a mesma sessão em
     * duas páginas e omitir outra, e o cliente nunca saberia. É a mesma armadilha que EST-C012
     * corrigiu no ledger de estoque.
     *
     * <p>Ordena por {@code id DESC} — chave única e monotônica, então o desempate é dispensável
     * (ao contrário do ledger, onde {@code created_at} repetia dentro da mesma transação). Mais
     * recentes primeiro, como {@code /pdv/sessions/&#123;id&#125;/sales} e a listagem de mesas.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> findAll(int page, int size) {
        Page<CashRegisterSessionEntity> result = cashRegisterSessionJpaRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
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
