package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashMovementTest {

    private static CashMovement sangria(BigDecimal amount) {
        return CashMovement.register(1L, CashMovementType.SANGRIA, amount, "depósito no cofre", "caixa1");
    }

    @Test
    void register_createsWithoutIdAtCurrentInstant() {
        CashMovement movement = sangria(new BigDecimal("150.00"));

        assertThat(movement.id()).isNull();
        assertThat(movement.sessionId()).isEqualTo(1L);
        assertThat(movement.type()).isEqualTo(CashMovementType.SANGRIA);
        assertThat(movement.amount()).isEqualByComparingTo("150.00");
        assertThat(movement.createdAt()).isNotNull();
    }

    @Test
    void signedAmount_derivesTheSignFromTheTypeNotTheValue() {
        assertThat(sangria(new BigDecimal("150.00")).signedAmount()).isEqualByComparingTo("-150.00");
        assertThat(CashMovement.register(1L, CashMovementType.SUPRIMENTO, new BigDecimal("150.00"),
                "reforço de troco", "caixa1").signedAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void typeSignum_matchesSignedAmount() {
        assertThat(CashMovementType.SANGRIA.signum()).isEqualTo(-1);
        assertThat(CashMovementType.SUPRIMENTO.signum()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveAmount() {
        // Valor negativo seria uma segunda forma de expressar sangria, e todo SUM passaria a
        // depender de quem gravou.
        assertThatThrownBy(() -> sangria(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sinal vem do type");
        assertThatThrownBy(() -> sangria(new BigDecimal("-150.00")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sinal vem do type");
    }

    @Test
    void rejectsBlankReason() {
        assertThatThrownBy(() -> CashMovement.register(1L, CashMovementType.SANGRIA, BigDecimal.TEN,
                "  ", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indistinguível de desvio");
    }

    @Test
    void rejectsMissingSessionTypeOrUser() {
        assertThatThrownBy(() -> CashMovement.register(null, CashMovementType.SANGRIA, BigDecimal.TEN,
                "motivo", "caixa1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashMovement.register(1L, null, BigDecimal.TEN, "motivo", "caixa1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashMovement.register(1L, CashMovementType.SANGRIA, BigDecimal.TEN,
                "motivo", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant createdAt = Instant.parse("2026-07-28T14:30:00Z");
        CashMovement movement = CashMovement.of(9L, 1L, CashMovementType.SUPRIMENTO,
                new BigDecimal("80.00"), "reforço de troco", "gerente", createdAt);

        assertThat(movement.id()).isEqualTo(9L);
        assertThat(movement.createdAt()).isEqualTo(createdAt);
        assertThat(movement.signedAmount()).isEqualByComparingTo("80.00");
    }
}
