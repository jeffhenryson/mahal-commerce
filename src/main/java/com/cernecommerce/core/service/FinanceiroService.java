package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.financeiro.CashFlowEntryNotFoundException;
import com.cernecommerce.core.domain.exception.financeiro.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import com.cernecommerce.core.ports.out.financeiro.LedgerRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class FinanceiroService implements FinanceiroUseCase {

    /** Mesmo teto de {@code OrderReportService} — única defesa hoje contra a consulta agregada
     * mais cara do domínio, sem rate limit de verdade ainda (PLAT-C030). */
    private static final long MAX_RANGE_DAYS = 366;

    private final LedgerRepository ledgerRepository;

    public FinanceiroService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashFlowEntry> listCashFlow(int page, int size) {
        return ledgerRepository.findAll(page, size);
    }

    @Override
    public CashFlowEntry createCashFlowEntry(String description, String entityName, CashFlowCategory category,
            Direction direction, BigDecimal amount, LocalDate dueDate, LinkedEntityType linkedEntityType,
            Long linkedEntityId) {
        CashFlowEntry entry = new CashFlowEntry(null, LocalDate.now(), description, entityName, category,
                direction, amount, CashFlowStatus.PREVISTO, dueDate, null, linkedEntityType, linkedEntityId, null);
        return ledgerRepository.save(entry);
    }

    @Override
    public CashFlowEntry updateCashFlowEntry(Long id, String description, String entityName,
            CashFlowCategory category, Direction direction, BigDecimal amount, LocalDate dueDate,
            CashFlowStatus status, LocalDate paymentDate, LinkedEntityType linkedEntityType, Long linkedEntityId) {
        CashFlowEntry current = ledgerRepository.findById(id)
                .orElseThrow(() -> new CashFlowEntryNotFoundException(id));

        CashFlowStatus effectiveStatus = status != null ? status : current.status();
        // Marcar PAGO sem informar paymentDate grava a data corrente — pedido explícito do front
        // no contrato do FIN-F004.
        LocalDate effectivePaymentDate = paymentDate != null ? paymentDate
                : (status == CashFlowStatus.PAGO && current.paymentDate() == null
                        ? LocalDate.now() : current.paymentDate());

        CashFlowEntry updated = new CashFlowEntry(
                current.id(),
                current.date(),
                description != null ? description : current.description(),
                entityName != null ? entityName : current.entityName(),
                category != null ? category : current.category(),
                direction != null ? direction : current.direction(),
                amount != null ? amount : current.amount(),
                effectiveStatus,
                dueDate != null ? dueDate : current.dueDate(),
                effectivePaymentDate,
                linkedEntityType != null ? linkedEntityType : current.linkedEntityType(),
                linkedEntityId != null ? linkedEntityId : current.linkedEntityId(),
                current.deletedAt());
        return ledgerRepository.save(updated);
    }

    @Override
    public void deleteCashFlowEntry(Long id) {
        CashFlowEntry current = ledgerRepository.findById(id)
                .orElseThrow(() -> new CashFlowEntryNotFoundException(id));
        // Preferir soft-delete quando há linkedEntityId — não perder o vínculo com o pedido/venda
        // de origem (contrato do FIN-F004).
        if (current.linkedEntityId() != null) {
            ledgerRepository.softDelete(id);
        } else {
            ledgerRepository.hardDelete(id);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CashFlowSummary getCashFlowSummary(LocalDate from, LocalDate to) {
        validatePeriod(from, to);
        return ledgerRepository.summarize(from, to);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new InvalidReportPeriodException("'from' não pode ser depois de 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new InvalidReportPeriodException("Intervalo máximo permitido: " + MAX_RANGE_DAYS + " dias");
        }
    }
}
