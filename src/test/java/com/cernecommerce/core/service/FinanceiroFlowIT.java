package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.financeiro.CashFlowEntryNotFoundException;
import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import com.cernecommerce.core.ports.in.FinanceiroUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fluxo completo do contrato de escrita FIN-F004, de ponta a ponta contra banco real:
 * create → patch (marcar pago) → summary → delete.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class FinanceiroFlowIT {

    @Autowired
    FinanceiroUseCase financeiroUseCase;

    @PersistenceContext
    EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void createPatchSummarizeAndDelete_manualEntry_withoutLinkedEntity_hardDeletes() {
        LocalDate dueDate = LocalDate.now().plusDays(10);

        CashFlowEntry created = financeiroUseCase.createCashFlowEntry("Aluguel Loja Julho/2026",
                "Imobiliária Central RJ", CashFlowCategory.ALUGUEL, Direction.OUTFLOW,
                new BigDecimal("8500.00"), dueDate, null, null);
        flushAndClear();

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(CashFlowStatus.PREVISTO);
        assertThat(created.date()).isEqualTo(LocalDate.now());
        assertThat(created.paymentDate()).isNull();

        CashFlowEntry paid = financeiroUseCase.updateCashFlowEntry(created.id(), null, null, null, null, null,
                null, CashFlowStatus.PAGO, null, null, null);
        flushAndClear();

        assertThat(paid.status()).isEqualTo(CashFlowStatus.PAGO);
        assertThat(paid.paymentDate()).isEqualTo(LocalDate.now());
        assertThat(paid.amount()).isEqualByComparingTo("8500.00");

        CashFlowSummary summary = financeiroUseCase.getCashFlowSummary(dueDate.minusDays(1), dueDate.plusDays(1));
        assertThat(summary.totalOutflow()).isGreaterThanOrEqualTo(new BigDecimal("8500.00"));
        assertThat(summary.entryCount()).isGreaterThanOrEqualTo(1);

        financeiroUseCase.deleteCashFlowEntry(created.id());
        flushAndClear();

        assertThatThrownBy(() -> financeiroUseCase.updateCashFlowEntry(created.id(), "x", null, null, null, null,
                null, null, null, null, null))
                .isInstanceOf(CashFlowEntryNotFoundException.class);
    }

    @Test
    void delete_withLinkedEntity_softDeletes_andEntryDisappearsFromListing() {
        CashFlowEntry created = financeiroUseCase.createCashFlowEntry("Venda balcão #9001", null,
                CashFlowCategory.VENDA_PDV, Direction.INFLOW, new BigDecimal("150.00"),
                LocalDate.now(), LinkedEntityType.ORDER, 9001L);
        flushAndClear();

        financeiroUseCase.deleteCashFlowEntry(created.id());
        flushAndClear();

        boolean stillListed = financeiroUseCase.listCashFlow(0, 100).content().stream()
                .anyMatch(e -> e.id().equals(created.id()));
        assertThat(stillListed).isFalse();

        assertThatThrownBy(() -> financeiroUseCase.deleteCashFlowEntry(created.id()))
                .isInstanceOf(CashFlowEntryNotFoundException.class);
    }
}
