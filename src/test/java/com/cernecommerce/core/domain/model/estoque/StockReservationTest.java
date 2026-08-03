package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockReservationTest {

    private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
    private static final Instant LATER = NOW.plus(30, ChronoUnit.MINUTES);

    @Test
    void create_construiReservaAtivaSemIdNemResolvedAt() {
        Instant futureExpiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        StockReservation reservation = StockReservation.create("NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT-1", futureExpiresAt, "gerente");

        assertThat(reservation.id()).isNull();
        assertThat(reservation.status()).isEqualTo(ReservationStatus.ACTIVE);
        assertThat(reservation.resolvedAt()).isNull();
        assertThat(reservation.expiresAt()).isEqualTo(futureExpiresAt);
        assertThat(reservation.createdAt()).isNotNull();
        assertThat(reservation.username()).isEqualTo("gerente");
    }

    @Test
    void of_reconstituiDaPersistencia() {
        StockReservation reservation = StockReservation.of(9L, "NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT-1", ReservationStatus.CONSUMED, LATER, NOW, LATER, "gerente");

        assertThat(reservation.id()).isEqualTo(9L);
        assertThat(reservation.status()).isEqualTo(ReservationStatus.CONSUMED);
        assertThat(reservation.resolvedAt()).isEqualTo(LATER);
    }

    @Test
    void throwsWhenSkuEhBlank() {
        assertThatThrownBy(() -> StockReservation.create(" ", 1L, BigDecimal.ONE, "CHECKOUT-1", LATER, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdEhNulo() {
        assertThatThrownBy(() -> StockReservation.create("NARG-001", null, BigDecimal.ONE, "CHECKOUT-1", LATER, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityEhZeroOuNegativa() {
        assertThatThrownBy(() -> StockReservation.create("NARG-001", 1L, BigDecimal.ZERO, "CHECKOUT-1", LATER, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockReservation.create("NARG-001", 1L, new BigDecimal("-1"), "CHECKOUT-1", LATER, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenOwnerReferenceEhBlank() {
        assertThatThrownBy(() -> StockReservation.create("NARG-001", 1L, BigDecimal.ONE, " ", LATER, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenStatusEhNulo() {
        assertThatThrownBy(() -> StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                null, LATER, NOW, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenCreatedAtEhNulo() {
        assertThatThrownBy(() -> StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, LATER, null, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenExpiresAtEhNulo() {
        assertThatThrownBy(() -> StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, null, NOW, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenExpiresAtNaoEhPosteriorACreatedAt() {
        assertThatThrownBy(() -> StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, NOW, NOW, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenReservaAtivaTemResolvedAt() {
        assertThatThrownBy(() -> StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, LATER, NOW, LATER, "gerente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACTIVE não pode ter resolvedAt");
    }

    @Test
    void throwsWhenReservaEncerradaNaoTemResolvedAt() {
        assertThatThrownBy(() -> StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.CONSUMED, LATER, NOW, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encerrada exige resolvedAt");
    }

    @Test
    void isActive_apenasParaStatusActive() {
        StockReservation active = StockReservation.create("NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                Instant.now().plus(30, ChronoUnit.MINUTES), "gerente");
        StockReservation consumed = StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.CONSUMED, LATER, NOW, LATER, "gerente");

        assertThat(active.isActive()).isTrue();
        assertThat(consumed.isActive()).isFalse();
    }

    @Test
    void isExpiredAt_ativaEJaPassouDoPrazo_ehTrue() {
        StockReservation reservation = StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, LATER, NOW, null, "gerente");

        assertThat(reservation.isExpiredAt(LATER)).isTrue();
        assertThat(reservation.isExpiredAt(LATER.plusSeconds(1))).isTrue();
    }

    @Test
    void isExpiredAt_ativaEAindaDentroDoPrazo_ehFalse() {
        StockReservation reservation = StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, LATER, NOW, null, "gerente");

        assertThat(reservation.isExpiredAt(LATER.minusSeconds(1))).isFalse();
    }

    @Test
    void isExpiredAt_statusTerminal_ehSempreFalseMesmoPassadoOPrazo() {
        StockReservation consumed = StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.CONSUMED, LATER, NOW, LATER, "gerente");

        assertThat(consumed.isExpiredAt(LATER.plus(1, ChronoUnit.DAYS))).isFalse();
    }

    @Test
    void consumed_marcaStatusEResolvedAtPreservandoOResto() {
        StockReservation reservation = StockReservation.of(1L, "NARG-001", 1L, new BigDecimal("2.000"),
                "CHECKOUT-1", ReservationStatus.ACTIVE, LATER, NOW, null, "gerente");

        StockReservation result = reservation.consumed();

        assertThat(result.status()).isEqualTo(ReservationStatus.CONSUMED);
        assertThat(result.resolvedAt()).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.warehouseId()).isEqualTo(1L);
        assertThat(result.quantity()).isEqualByComparingTo("2.000");
        assertThat(result.ownerReference()).isEqualTo("CHECKOUT-1");
        assertThat(result.createdAt()).isEqualTo(NOW);
        assertThat(result.username()).isEqualTo("gerente");
    }

    @Test
    void released_marcaStatusReleasedEResolvedAt() {
        StockReservation reservation = StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, LATER, NOW, null, "gerente");

        StockReservation result = reservation.released();

        assertThat(result.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(result.resolvedAt()).isNotNull();
    }

    @Test
    void expired_marcaStatusExpiredEResolvedAt() {
        StockReservation reservation = StockReservation.of(1L, "NARG-001", 1L, BigDecimal.ONE, "CHECKOUT-1",
                ReservationStatus.ACTIVE, LATER, NOW, null, "gerente");

        StockReservation result = reservation.expired();

        assertThat(result.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(result.resolvedAt()).isNotNull();
    }
}
