package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationIntegrityMismatchTest {

    @Test
    void of_reconstitutesTheDiagnosticRow() {
        ReservationIntegrityMismatch mismatch = ReservationIntegrityMismatch.of("NARG-999", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));

        assertThat(mismatch.sku()).isEqualTo("NARG-999");
        assertThat(mismatch.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(mismatch.reservedQuantity()).isEqualByComparingTo("5.000");
        assertThat(mismatch.activeReservationsTotal()).isEqualByComparingTo("3.000");
    }

    @Test
    void difference_isReservedMinusActiveTotal() {
        ReservationIntegrityMismatch contadorAcima = ReservationIntegrityMismatch.of("NARG-999", "LOJA-01",
                new BigDecimal("5.000"), new BigDecimal("3.000"));
        ReservationIntegrityMismatch ledgerAcima = ReservationIntegrityMismatch.of("NARG-999", "LOJA-01",
                new BigDecimal("0"), new BigDecimal("2.000"));

        assertThat(contadorAcima.difference()).isEqualByComparingTo("2.000");
        assertThat(ledgerAcima.difference()).isEqualByComparingTo("-2.000");
    }

    /** Par presente só de um dos dois lados da união devolve null da consulta; aqui vira zero. */
    @Test
    void nullQuantitiesBecomeZero() {
        ReservationIntegrityMismatch mismatch = ReservationIntegrityMismatch.of("NARG-999", "LOJA-01", null, null);

        assertThat(mismatch.reservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mismatch.activeReservationsTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(mismatch.difference()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> ReservationIntegrityMismatch.of("  ", "LOJA-01", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> ReservationIntegrityMismatch.of(null, "LOJA-01", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");
    }

    @Test
    void throwsWhenWarehouseCodeIsBlank() {
        assertThatThrownBy(() -> ReservationIntegrityMismatch.of("NARG-999", "  ", BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseCode");
    }

    @Test
    void throwsWhenWarehouseCodeIsNull() {
        assertThatThrownBy(() -> ReservationIntegrityMismatch.of("NARG-999", null, BigDecimal.ONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseCode");
    }
}
