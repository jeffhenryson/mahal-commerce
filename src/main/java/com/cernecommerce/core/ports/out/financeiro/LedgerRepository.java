package com.cernecommerce.core.ports.out.financeiro;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de lançamentos do razão/fluxo de caixa (FIN-F004).
 */
public interface LedgerRepository {

    PageResult<CashFlowEntry> findAll(int page, int size);

    List<CashFlowEntry> findByPeriod(LocalDate from, LocalDate to);

    Optional<CashFlowEntry> findById(Long id);

    CashFlowEntry save(CashFlowEntry entry);

    /** Marca {@code deletedAt}, preservando a linha — usado quando há {@code linkedEntityId}. */
    void softDelete(Long id);

    /** Remove a linha de verdade — usado quando não há vínculo com outro domínio a preservar. */
    void hardDelete(Long id);

    CashFlowSummary summarize(LocalDate from, LocalDate to);
}
