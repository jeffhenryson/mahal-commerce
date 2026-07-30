package com.cernecommerce.core.domain.model.cashback;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashbackRateTest {

    @Test
    void global_hasNoScopeRefAndIsActive() {
        CashbackRate rate = CashbackRate.global(new BigDecimal("3.0"));

        assertThat(rate.scope()).isEqualTo(CashbackScope.GLOBAL);
        assertThat(rate.scopeRef()).isNull();
        assertThat(rate.active()).isTrue();
        assertThat(rate.validTo()).isNull();
    }

    @Test
    void forCategoryAndForSku_requireScopeRef() {
        CashbackRate category = CashbackRate.forCategory("narguile", new BigDecimal("6.5"));
        assertThat(category.scope()).isEqualTo(CashbackScope.CATEGORY);
        assertThat(category.scopeRef()).isEqualTo("narguile");

        CashbackRate sku = CashbackRate.forSku("CARVAO-1KG", new BigDecimal("2.5"));
        assertThat(sku.scope()).isEqualTo(CashbackScope.SKU);
        assertThat(sku.scopeRef()).isEqualTo("CARVAO-1KG");
    }

    @Test
    void rejectsScopeRefMismatch() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CashbackRate(null, CashbackScope.GLOBAL, "algo", BigDecimal.TEN, true, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("GLOBAL");
        assertThatThrownBy(() -> new CashbackRate(null, CashbackScope.CATEGORY, null, BigDecimal.TEN, true, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scopeRef");
        assertThatThrownBy(() -> new CashbackRate(null, CashbackScope.SKU, "  ", BigDecimal.TEN, true, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("scopeRef");
    }

    @Test
    void rejectsPercentOutOfRange() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CashbackRate(null, CashbackScope.GLOBAL, null, new BigDecimal("-1"), true, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("percent");
        assertThatThrownBy(() -> new CashbackRate(null, CashbackScope.GLOBAL, null, new BigDecimal("100.01"), true, now, null, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("percent");
    }

    @Test
    void rejectsValidToNotAfterValidFrom() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new CashbackRate(null, CashbackScope.GLOBAL, null, BigDecimal.TEN, true, now, now, now))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("validTo");
    }

    @Test
    void appliesAt_respectsActiveAndValidityWindow() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        CashbackRate rate = CashbackRate.of(1L, CashbackScope.GLOBAL, null, BigDecimal.TEN, true, from, to, from);

        assertThat(rate.appliesAt(from)).isTrue();
        assertThat(rate.appliesAt(from.minus(1, ChronoUnit.DAYS))).isFalse();
        assertThat(rate.appliesAt(to)).isFalse();
        assertThat(rate.appliesAt(to.minus(1, ChronoUnit.DAYS))).isTrue();

        CashbackRate inactive = rate.withPatch(null, false, null);
        assertThat(inactive.appliesAt(from)).isFalse();
    }

    @Test
    void priority_ordersSkuBeforeCategoryBeforeGlobal() {
        assertThat(CashbackRate.forSku("X", BigDecimal.ZERO).priority()).isLessThan(
                CashbackRate.forCategory("Y", BigDecimal.ZERO).priority());
        assertThat(CashbackRate.forCategory("Y", BigDecimal.ZERO).priority()).isLessThan(
                CashbackRate.global(BigDecimal.ZERO).priority());
    }

    @Test
    void withPatch_nullKeepsExistingValue() {
        CashbackRate rate = CashbackRate.global(new BigDecimal("3.0"));

        CashbackRate patched = rate.withPatch(new BigDecimal("5.0"), null, null);
        assertThat(patched.percent()).isEqualByComparingTo("5.0");
        assertThat(patched.active()).isEqualTo(rate.active());
        assertThat(patched.validTo()).isEqualTo(rate.validTo());
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant createdAt = Instant.parse("2026-07-29T14:30:00Z");
        CashbackRate rate = CashbackRate.of(9L, CashbackScope.SKU, "CARVAO-1KG", new BigDecimal("2.5"),
                true, createdAt, null, createdAt);

        assertThat(rate.id()).isEqualTo(9L);
        assertThat(rate.scope()).isEqualTo(CashbackScope.SKU);
        assertThat(rate.createdAt()).isEqualTo(createdAt);
    }
}
