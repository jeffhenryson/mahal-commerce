package com.cernecommerce.core.domain.model.cashback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CashbackBalanceTest {

    @Test
    void availableNuloViraZero() {
        CashbackBalance balance = new CashbackBalance(null, new BigDecimal("5.00"), new BigDecimal("1.00"));

        assertThat(balance.available()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void pendingNuloViraZero() {
        CashbackBalance balance = new CashbackBalance(new BigDecimal("5.00"), null, new BigDecimal("1.00"));

        assertThat(balance.pending()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void expiringSoonNuloViraZero() {
        CashbackBalance balance = new CashbackBalance(new BigDecimal("5.00"), new BigDecimal("1.00"), null);

        assertThat(balance.expiringSoon()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void todosOsCamposNulosViramZero() {
        CashbackBalance balance = new CashbackBalance(null, null, null);

        assertThat(balance.available()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.pending()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.expiringSoon()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void valoresNaoNulosSaoPreservados() {
        CashbackBalance balance = new CashbackBalance(new BigDecimal("10.50"), new BigDecimal("3.25"),
                new BigDecimal("2.00"));

        assertThat(balance.available()).isEqualByComparingTo("10.50");
        assertThat(balance.pending()).isEqualByComparingTo("3.25");
        assertThat(balance.expiringSoon()).isEqualByComparingTo("2.00");
    }
}
