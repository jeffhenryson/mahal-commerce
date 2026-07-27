package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrphanSkuTest {

    @Test
    void of_reconstitutesTheDiagnosticRow() {
        Instant lastMovement = Instant.parse("2026-07-01T10:00:00Z");
        OrphanSku orphan = OrphanSku.of("NARG-999", "LOJA-01", new BigDecimal("7.500"), 3L, true, lastMovement);

        assertThat(orphan.sku()).isEqualTo("NARG-999");
        assertThat(orphan.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(orphan.quantity()).isEqualByComparingTo("7.500");
        assertThat(orphan.movementCount()).isEqualTo(3L);
        assertThat(orphan.hasReorderPoint()).isTrue();
        assertThat(orphan.lastMovementAt()).isEqualTo(lastMovement);
    }

    /** Órfão presente só em stock_movement ou só em stock_reorder_point não tem linha de saldo. */
    @Test
    void nullQuantityBecomesZero() {
        OrphanSku orphan = OrphanSku.of("NARG-999", "LOJA-01", null, 2L, false, Instant.now());

        assertThat(orphan.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /** Órfão que só tem saldo ou só tem ponto de reposição nunca foi movimentado. */
    @Test
    void allowsNullLastMovementAtWhenNeverMoved() {
        OrphanSku orphan = OrphanSku.of("NARG-999", "LOJA-01", new BigDecimal("4.000"), 0L, true, null);

        assertThat(orphan.lastMovementAt()).isNull();
        assertThat(orphan.movementCount()).isZero();
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> OrphanSku.of("  ", "LOJA-01", BigDecimal.ONE, 1L, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> OrphanSku.of(null, "LOJA-01", BigDecimal.ONE, 1L, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku");
    }

    @Test
    void throwsWhenWarehouseCodeIsBlank() {
        assertThatThrownBy(() -> OrphanSku.of("NARG-999", "  ", BigDecimal.ONE, 1L, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseCode");
    }

    @Test
    void throwsWhenWarehouseCodeIsNull() {
        assertThatThrownBy(() -> OrphanSku.of("NARG-999", null, BigDecimal.ONE, 1L, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouseCode");
    }

    @Test
    void throwsWhenMovementCountIsNegative() {
        assertThatThrownBy(() -> OrphanSku.of("NARG-999", "LOJA-01", BigDecimal.ONE, -1L, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("movementCount");
    }
}
