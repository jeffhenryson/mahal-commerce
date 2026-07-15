package com.cernecommerce.core.ports.out.financeiro;

import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;

import java.time.LocalDate;
import java.util.List;

/**
 * Port de saída para persistência de lançamentos do razão/fluxo de caixa.
 *
 * <p>Stub — a ser implementado por um adapter em {@code adapter/out/persistence}.</p>
 */
public interface LedgerRepository {

    List<CashFlowEntry> findAll();

    List<CashFlowEntry> findByPeriod(LocalDate from, LocalDate to);

    CashFlowEntry save(CashFlowEntry entry);
}
