package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.LedgerEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerJpaRepository extends JpaRepository<LedgerEntity, Long> {

    Page<LedgerEntity> findByDeletedAtIsNullOrderByIdDesc(Pageable pageable);

    Optional<LedgerEntity> findByIdAndDeletedAtIsNull(Long id);

    List<LedgerEntity> findByDueDateBetweenAndDeletedAtIsNullOrderByDueDateAsc(LocalDate from, LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.direction = 'INFLOW' THEN e.amount ELSE 0 END), 0) AS totalInflow,
                   COALESCE(SUM(CASE WHEN e.direction = 'OUTFLOW' THEN e.amount ELSE 0 END), 0) AS totalOutflow,
                   COUNT(e) AS entryCount
            FROM LedgerEntity e
            WHERE e.deletedAt IS NULL AND e.dueDate >= :from AND e.dueDate <= :to
            """)
    CashFlowSummaryProjection summarize(@Param("from") LocalDate from, @Param("to") LocalDate to);

    interface CashFlowSummaryProjection {
        BigDecimal getTotalInflow();

        BigDecimal getTotalOutflow();

        long getEntryCount();
    }
}
