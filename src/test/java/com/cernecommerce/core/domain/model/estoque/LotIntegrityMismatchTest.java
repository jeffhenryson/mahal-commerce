package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LotIntegrityMismatchTest {

    @Test
    void of_reconstitutesTheDiagnosticRow() {
        LotIntegrityMismatch mismatch = LotIntegrityMismatch.of("ESS-999", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));

        assertThat(mismatch.sku()).isEqualTo("ESS-999");
        assertThat(mismatch.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(mismatch.balanceQuantity()).isEqualByComparingTo("5.000");
        assertThat(mismatch.lotsTotal()).isEqualByComparingTo("3.000");
    }

    @Test
    void difference_isBalanceMinusLotsTotal() {
        LotIntegrityMismatch agregadoAcima = LotIntegrityMismatch.of("ESS-999", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));
        LotIntegrityMismatch lotesAcima = LotIntegrityMismatch.of("ESS-999", "LOJA-01",
                new BigDecimal("0"), new BigDecimal("2.000"));

        assertThat(agregadoAcima.difference()).isEqualByComparingTo("2.000");
        assertThat(lotesAcima.difference()).isEqualByComparingTo("-2.000");
    }

    /** Par presente só de um dos dois lados da união devolve null da consulta; aqui vira zero. */
    @Test
    void nullQuantitiesBecomeZero() {
        LotIntegrityMismatch mismatch = LotIntegrityMismatch.of("ESS-999", "LOJA-01", null, null);

        assertThat(mismatch.balanceQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mismatch.lotsTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mismatch.difference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> LotIntegrityMismatch.of("  ", "LOJA-01", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> LotIntegrityMismatch.of(null, "LOJA-01", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");
    }

    @Test
    void throwsWhenWarehouseCodeIsBlank() {
        assertThatThrownBy(() -> LotIntegrityMismatch.of("ESS-999", "  ", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseCode");
    }

    @Test
    void throwsWhenWarehouseCodeIsNull() {
        assertThatThrownBy(() -> LotIntegrityMismatch.of("ESS-999", null, BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseCode");
    }
}
