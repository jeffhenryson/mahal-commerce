package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMovementTest {

    @Test
    void create_buildsMovementWithoutIdAndCurrentTimestamp() {
        StockMovement movement = StockMovement.create("NARG-001", 1L, MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento de fornecedor", "gerente");

        assertThat(movement.id()).isNull();
        assertThat(movement.sku()).isEqualTo("NARG-001");
        assertThat(movement.warehouseId()).isEqualTo(1L);
        assertThat(movement.type()).isEqualTo(MovementType.ENTRADA);
        assertThat(movement.quantity()).isEqualByComparingTo("5.000");
        assertThat(movement.reason()).isEqualTo("Recebimento de fornecedor");
        assertThat(movement.username()).isEqualTo("gerente");
        assertThat(movement.createdAt()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant createdAt = Instant.parse("2026-07-01T10:00:00Z");
        StockMovement movement = StockMovement.of(9L, "NARG-001", 1L, MovementType.SAIDA,
                new BigDecimal("2.000"), "Quebra", "gerente", createdAt);

        assertThat(movement.id()).isEqualTo(9L);
        assertThat(movement.type()).isEqualTo(MovementType.SAIDA);
        assertThat(movement.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> StockMovement.create("  ", 1L, MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> StockMovement.create("NARG-001", null, MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenTypeIsNull() {
        assertThatThrownBy(() -> StockMovement.create("NARG-001", 1L, null,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsZeroOrNegative() {
        assertThatThrownBy(() -> StockMovement.create("NARG-001", 1L, MovementType.ENTRADA,
                BigDecimal.ZERO, "motivo", "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StockMovement.create("NARG-001", 1L, MovementType.ENTRADA,
                new BigDecimal("-1"), "motivo", "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenReasonIsBlank() {
        assertThatThrownBy(() -> StockMovement.create("NARG-001", 1L, MovementType.ENTRADA,
                BigDecimal.ONE, "  ", "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenUsernameIsBlank() {
        assertThatThrownBy(() -> StockMovement.create("NARG-001", 1L, MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
