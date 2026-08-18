package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.financeiro.CashFlowEntryNotFoundException;
import com.cernecommerce.core.domain.exception.financeiro.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import com.cernecommerce.core.ports.out.financeiro.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceiroServiceTest {

    @Mock
    LedgerRepository ledgerRepository;

    FinanceiroService financeiroService;

    @BeforeEach
    void setUp() {
        financeiroService = new FinanceiroService(ledgerRepository);
    }

    private CashFlowEntry entry(Long id, CashFlowStatus status, Long linkedEntityId) {
        return new CashFlowEntry(id, LocalDate.now(), "Aluguel Loja", "Imobiliária Central", CashFlowCategory.ALUGUEL,
                Direction.OUTFLOW, new BigDecimal("8500.00"), status, LocalDate.now().plusDays(5), null, null,
                linkedEntityId, null);
    }

    @Test
    void listCashFlow_delegatesToRepository() {
        PageResult<CashFlowEntry> page = new PageResult<>(List.of(entry(1L, CashFlowStatus.PREVISTO, null)), 0, 20, 1, 1);
        when(ledgerRepository.findAll(0, 20)).thenReturn(page);

        PageResult<CashFlowEntry> result = financeiroService.listCashFlow(0, 20);

        assertThat(result).isEqualTo(page);
    }

    @Test
    void createCashFlowEntry_startsAsPrevistoWithTodaysDate() {
        ArgumentCaptor<CashFlowEntry> captor = ArgumentCaptor.forClass(CashFlowEntry.class);
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashFlowEntry created = financeiroService.createCashFlowEntry("Aluguel Loja Julho/2026",
                "Imobiliária Central RJ", CashFlowCategory.ALUGUEL, Direction.OUTFLOW, new BigDecimal("8500.00"),
                LocalDate.of(2026, 7, 10), null, null);

        verify(ledgerRepository).save(captor.capture());
        CashFlowEntry saved = captor.getValue();
        assertThat(saved.id()).isNull();
        assertThat(saved.date()).isEqualTo(LocalDate.now());
        assertThat(saved.status()).isEqualTo(CashFlowStatus.PREVISTO);
        assertThat(saved.dueDate()).isEqualTo(LocalDate.of(2026, 7, 10));
        assertThat(saved.paymentDate()).isNull();
        assertThat(created).isEqualTo(saved);
    }

    @Test
    void updateCashFlowEntry_markingPaidWithoutPaymentDate_stampsCurrentDate() {
        CashFlowEntry current = entry(1L, CashFlowStatus.PREVISTO, null);
        when(ledgerRepository.findById(1L)).thenReturn(Optional.of(current));
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashFlowEntry updated = financeiroService.updateCashFlowEntry(1L, null, null, null, null, null, null,
                CashFlowStatus.PAGO, null, null, null);

        assertThat(updated.status()).isEqualTo(CashFlowStatus.PAGO);
        assertThat(updated.paymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void updateCashFlowEntry_absentFields_keepCurrentValues() {
        CashFlowEntry current = entry(1L, CashFlowStatus.PREVISTO, null);
        when(ledgerRepository.findById(1L)).thenReturn(Optional.of(current));
        when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashFlowEntry updated = financeiroService.updateCashFlowEntry(1L, "Novo texto", null, null, null, null,
                null, null, null, null, null);

        assertThat(updated.description()).isEqualTo("Novo texto");
        assertThat(updated.entityName()).isEqualTo(current.entityName());
        assertThat(updated.category()).isEqualTo(current.category());
        assertThat(updated.amount()).isEqualTo(current.amount());
        assertThat(updated.status()).isEqualTo(current.status());
    }

    @Test
    void updateCashFlowEntry_unknownId_throwsNotFound() {
        when(ledgerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financeiroService.updateCashFlowEntry(99L, null, null, null, null, null, null,
                null, null, null, null))
                .isInstanceOf(CashFlowEntryNotFoundException.class);
    }

    @Test
    void deleteCashFlowEntry_withLinkedEntity_softDeletes() {
        when(ledgerRepository.findById(1L)).thenReturn(Optional.of(entry(1L, CashFlowStatus.PREVISTO, 1042L)));

        financeiroService.deleteCashFlowEntry(1L);

        verify(ledgerRepository).softDelete(1L);
        verify(ledgerRepository, never()).hardDelete(any());
    }

    @Test
    void deleteCashFlowEntry_withoutLinkedEntity_hardDeletes() {
        when(ledgerRepository.findById(1L)).thenReturn(Optional.of(entry(1L, CashFlowStatus.PREVISTO, null)));

        financeiroService.deleteCashFlowEntry(1L);

        verify(ledgerRepository).hardDelete(1L);
        verify(ledgerRepository, never()).softDelete(any());
    }

    @Test
    void deleteCashFlowEntry_unknownId_throwsNotFound() {
        when(ledgerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> financeiroService.deleteCashFlowEntry(99L))
                .isInstanceOf(CashFlowEntryNotFoundException.class);
    }

    @Test
    void getCashFlowSummary_delegatesToRepository() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        CashFlowSummary summary = new CashFlowSummary(new BigDecimal("1000"), new BigDecimal("400"),
                new BigDecimal("600"), 5);
        when(ledgerRepository.summarize(from, to)).thenReturn(summary);

        assertThat(financeiroService.getCashFlowSummary(from, to)).isEqualTo(summary);
    }

    @Test
    void getCashFlowSummary_fromAfterTo_throwsInvalidPeriod() {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 7, 1);

        assertThatThrownBy(() -> financeiroService.getCashFlowSummary(from, to))
                .isInstanceOf(InvalidReportPeriodException.class);
    }

    @Test
    void getCashFlowSummary_rangeTooLong_throwsInvalidPeriod() {
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        assertThatThrownBy(() -> financeiroService.getCashFlowSummary(from, to))
                .isInstanceOf(InvalidReportPeriodException.class);
    }
}
