package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceiroServiceTest {

    FinanceiroService financeiroService;

    @BeforeEach
    void setUp() {
        financeiroService = new FinanceiroService();
    }

    @Test
    void listCashFlow_returnsEmptyPageResultWhileStub() {
        PageResult<CashFlowEntry> result = financeiroService.listCashFlow(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listCashFlow_echoesRequestedPageAndSize() {
        PageResult<CashFlowEntry> result = financeiroService.listCashFlow(2, 5);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void listCashFlow_returnsImmutableContentList() {
        PageResult<CashFlowEntry> result = financeiroService.listCashFlow(0, 20);

        assertThatThrownBy(() -> result.content().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listCashFlow_isConsistentAcrossCalls() {
        PageResult<CashFlowEntry> first = financeiroService.listCashFlow(0, 20);
        PageResult<CashFlowEntry> second = financeiroService.listCashFlow(0, 20);

        assertThat(first).isEqualTo(second);
    }
}
