package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.LedgerEntity;
import com.cernecommerce.core.domain.exception.financeiro.CashFlowEntryNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import com.cernecommerce.core.ports.out.financeiro.LedgerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class LedgerRepositoryImpl implements LedgerRepository {

    private final LedgerJpaRepository ledgerJpaRepository;

    public LedgerRepositoryImpl(LedgerJpaRepository ledgerJpaRepository) {
        this.ledgerJpaRepository = ledgerJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashFlowEntry> findAll(int page, int size) {
        Page<LedgerEntity> result = ledgerJpaRepository.findByDeletedAtIsNullOrderByIdDesc(
                PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashFlowEntry> findByPeriod(LocalDate from, LocalDate to) {
        return ledgerJpaRepository.findByDueDateBetweenAndDeletedAtIsNullOrderByDueDateAsc(from, to).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashFlowEntry> findById(Long id) {
        return ledgerJpaRepository.findByIdAndDeletedAtIsNull(id).map(this::toDomain);
    }

    @Override
    public CashFlowEntry save(CashFlowEntry entry) {
        LedgerEntity entity = entry.id() != null
                ? ledgerJpaRepository.findById(entry.id())
                        .orElseThrow(() -> new CashFlowEntryNotFoundException(entry.id()))
                : new LedgerEntity();
        entity.setDate(entry.date());
        entity.setDescription(entry.description());
        entity.setEntityName(entry.entityName());
        entity.setCategory(entry.category().name());
        entity.setDirection(entry.direction().name());
        entity.setAmount(entry.amount());
        entity.setStatus(entry.status().name());
        entity.setDueDate(entry.dueDate());
        entity.setPaymentDate(entry.paymentDate());
        entity.setLinkedEntityType(entry.linkedEntityType() == null ? null : entry.linkedEntityType().name());
        entity.setLinkedEntityId(entry.linkedEntityId());
        entity.setDeletedAt(entry.deletedAt());
        return toDomain(ledgerJpaRepository.save(entity));
    }

    @Override
    public void softDelete(Long id) {
        LedgerEntity entity = ledgerJpaRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new CashFlowEntryNotFoundException(id));
        entity.setDeletedAt(Instant.now());
        ledgerJpaRepository.save(entity);
    }

    @Override
    public void hardDelete(Long id) {
        if (!ledgerJpaRepository.existsById(id)) {
            throw new CashFlowEntryNotFoundException(id);
        }
        ledgerJpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowSummary summarize(LocalDate from, LocalDate to) {
        LedgerJpaRepository.CashFlowSummaryProjection projection = ledgerJpaRepository.summarize(from, to);
        BigDecimal totalInflow = projection.getTotalInflow();
        BigDecimal totalOutflow = projection.getTotalOutflow();
        return new CashFlowSummary(totalInflow, totalOutflow, totalInflow.subtract(totalOutflow),
                projection.getEntryCount());
    }

    private CashFlowEntry toDomain(LedgerEntity e) {
        return new CashFlowEntry(e.getId(), e.getDate(), e.getDescription(), e.getEntityName(),
                CashFlowCategory.valueOf(e.getCategory()), Direction.valueOf(e.getDirection()), e.getAmount(),
                CashFlowStatus.valueOf(e.getStatus()), e.getDueDate(), e.getPaymentDate(),
                e.getLinkedEntityType() == null ? null : LinkedEntityType.valueOf(e.getLinkedEntityType()),
                e.getLinkedEntityId(), e.getDeletedAt());
    }
}
