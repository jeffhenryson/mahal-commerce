package com.cernecommerce.core.domain.model.cashback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashbackEntryTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

    @Test
    void earned_startsWithPositiveAmountAndNoReversal() {
        Instant availableAt = NOW.plus(7, ChronoUnit.DAYS);
        Instant expiresAt = availableAt.plus(180, ChronoUnit.DAYS);
        CashbackEntry entry = CashbackEntry.earned(1L, 2L, 3L, new BigDecimal("2.50"), NOW, availableAt, expiresAt);

        assertThat(entry.type()).isEqualTo(CashbackEntryType.EARNED);
        assertThat(entry.amount()).isEqualByComparingTo("2.50");
        assertThat(entry.availableAt()).isEqualTo(availableAt);
        assertThat(entry.expiresAt()).isEqualTo(expiresAt);
        assertThat(entry.reversesEntryId()).isNull();
        assertThat(entry.isAvailableAt(NOW)).isFalse();
        assertThat(entry.isAvailableAt(availableAt)).isTrue();
    }

    @Test
    void expired_isNegativeAndReferencesOriginal() {
        CashbackEntry earned = CashbackEntry.earned(1L, 2L, 3L, new BigDecimal("2.50"), NOW,
                NOW.plus(7, ChronoUnit.DAYS), NOW.plus(187, ChronoUnit.DAYS));
        CashbackEntry original = CashbackEntry.of(10L, earned.customerId(), earned.orderId(),
                earned.orderItemId(), earned.type(), earned.amount(), earned.availableAt(),
                earned.expiresAt(), null, earned.createdAt());

        Instant expiredAt = NOW.plus(187, ChronoUnit.DAYS);
        CashbackEntry expired = CashbackEntry.expired(original, expiredAt);

        assertThat(expired.type()).isEqualTo(CashbackEntryType.EXPIRED);
        assertThat(expired.amount()).isEqualByComparingTo("-2.50");
        assertThat(expired.reversesEntryId()).isEqualTo(10L);
        assertThat(expired.availableAt()).isEqualTo(expiredAt);
        assertThat(expired.isAvailableAt(expiredAt)).isTrue();
    }

    @Test
    void expired_rejectsNonEarnedOriginal() {
        CashbackEntry original = CashbackEntry.of(1L, 1L, 2L, null, CashbackEntryType.EXPIRED,
                new BigDecimal("-1.00"), NOW, null, 99L, NOW);

        assertThatThrownBy(() -> CashbackEntry.expired(original, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EARNED");
    }

    @Test
    void reversed_isNegativeAndReferencesOriginal() {
        CashbackEntry earned = CashbackEntry.earned(1L, 2L, 3L, new BigDecimal("2.50"), NOW,
                NOW.plus(7, ChronoUnit.DAYS), NOW.plus(187, ChronoUnit.DAYS));
        CashbackEntry original = CashbackEntry.of(10L, earned.customerId(), earned.orderId(),
                earned.orderItemId(), earned.type(), earned.amount(), earned.availableAt(),
                earned.expiresAt(), null, earned.createdAt());

        Instant reversedAt = NOW.plus(10, ChronoUnit.DAYS);
        CashbackEntry reversed = CashbackEntry.reversed(original, reversedAt);

        assertThat(reversed.type()).isEqualTo(CashbackEntryType.REVERSED);
        assertThat(reversed.amount()).isEqualByComparingTo("-2.50");
        assertThat(reversed.reversesEntryId()).isEqualTo(10L);
        assertThat(reversed.availableAt()).isEqualTo(reversedAt);
        assertThat(reversed.expiresAt()).isNull();
        assertThat(reversed.isAvailableAt(reversedAt)).isTrue();
    }

    @Test
    void reversed_rejectsNonEarnedOriginal() {
        CashbackEntry original = CashbackEntry.of(1L, 1L, 2L, null, CashbackEntryType.EXPIRED,
                new BigDecimal("-1.00"), NOW, null, 99L, NOW);

        assertThatThrownBy(() -> CashbackEntry.reversed(original, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EARNED");
    }

    @Test
    void rejectsAmountSignMismatchingType() {
        assertThatThrownBy(() -> CashbackEntry.of(1L, 1L, 2L, 3L, CashbackEntryType.EARNED,
                new BigDecimal("-1.00"), NOW, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EARNED");
        assertThatThrownBy(() -> CashbackEntry.of(1L, 1L, 2L, 3L, CashbackEntryType.EXPIRED,
                new BigDecimal("1.00"), NOW, null, 5L, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("EXPIRED");
    }

    @Test
    void rejectsZeroAmount() {
        assertThatThrownBy(() -> CashbackEntry.earned(1L, 2L, 3L, BigDecimal.ZERO, NOW, NOW, NOW.plus(1, ChronoUnit.DAYS)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zero");
    }

    @Test
    void nonEarnedTypesRequireReversesEntryId() {
        assertThatThrownBy(() -> CashbackEntry.of(1L, 1L, 2L, null, CashbackEntryType.EXPIRED,
                new BigDecimal("-1.00"), NOW, null, null, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reversesEntryId");
    }

    @Test
    void earned_rejectsExpiresAtNotAfterAvailableAt() {
        assertThatThrownBy(() -> CashbackEntry.earned(1L, 2L, 3L, BigDecimal.TEN, NOW, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expiresAt");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        CashbackEntry entry = CashbackEntry.of(42L, 1L, 2L, 3L, CashbackEntryType.EARNED,
                new BigDecimal("2.50"), NOW, NOW.plus(180, ChronoUnit.DAYS), null, NOW);

        assertThat(entry.id()).isEqualTo(42L);
        assertThat(entry.customerId()).isEqualTo(1L);
    }
}
