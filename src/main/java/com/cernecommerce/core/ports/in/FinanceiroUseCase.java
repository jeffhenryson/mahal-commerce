package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Port de entrada do domínio <b>financeiro</b>. Cobre o contrato de escrita fechado em FIN-F004
 * (fluxo de caixa manual e por vínculo com pedido/compra) — DRE simplificado e conciliação de
 * taxas de gateway seguem como TODO.
 */
public interface FinanceiroUseCase {

    /** Lista os lançamentos de fluxo de caixa paginados (exclui os removidos). */
    PageResult<CashFlowEntry> listCashFlow(int page, int size);

    /** Cria um lançamento. Nasce com {@code status: PREVISTO} e {@code date} = hoje. */
    CashFlowEntry createCashFlowEntry(String description, String entityName, CashFlowCategory category,
            Direction direction, BigDecimal amount, LocalDate dueDate, LinkedEntityType linkedEntityType,
            Long linkedEntityId);

    /**
     * Altera parcialmente um lançamento. Parâmetro {@code null} mantém o valor atual — mesma
     * semântica de PATCH usada no resto do projeto (ver {@code EstoqueUseCase.updateCategory}).
     * Marcar {@code status: PAGO} sem {@code paymentDate} grava a data corrente.
     */
    CashFlowEntry updateCashFlowEntry(Long id, String description, String entityName, CashFlowCategory category,
            Direction direction, BigDecimal amount, LocalDate dueDate, CashFlowStatus status,
            LocalDate paymentDate, LinkedEntityType linkedEntityType, Long linkedEntityId);

    /** Soft-delete quando há {@code linkedEntityId} (preserva o vínculo), hard-delete caso contrário. */
    void deleteCashFlowEntry(Long id);

    /** Saldo agregado (entradas, saídas, saldo líquido) no período — agregado no backend. */
    CashFlowSummary getCashFlowSummary(LocalDate from, LocalDate to);
}
