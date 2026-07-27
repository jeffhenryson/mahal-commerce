package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockBalanceTest {

    @Test
    void zero_buildsBalanceWithoutIdAndZeroQuantity() {
        StockBalance balance = StockBalance.zero("NARG-001", 1L);

        assertThat(balance.id()).isNull();
        assertThat(balance.sku()).isEqualTo("NARG-001");
        assertThat(balance.warehouseId()).isEqualTo(1L);
        assertThat(balance.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(balance.version()).isZero();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        StockBalance balance = StockBalance.of(5L, "NARG-001", 1L, new BigDecimal("12.500"), 3L);

        assertThat(balance.id()).isEqualTo(5L);
        assertThat(balance.quantity()).isEqualByComparingTo("12.500");
        assertThat(balance.version()).isEqualTo(3L);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> StockBalance.zero("  ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> StockBalance.of(null, "NARG-001", null, BigDecimal.ZERO, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsNegative() {
        assertThatThrownBy(() -> StockBalance.of(null, "NARG-001", 1L, new BigDecimal("-1"), 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apply_entrada_increasesQuantityAndKeepsVersion() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 3L);

        StockBalance result = balance.apply(MovementType.ENTRADA, new BigDecimal("2.000"));

        assertThat(result.quantity()).isEqualByComparingTo("7.000");
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void apply_saida_decreasesQuantity() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("2.000"));

        assertThat(result.quantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void apply_saida_allowsDrainingExactlyToZero() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        StockBalance result = balance.apply(MovementType.SAIDA, new BigDecimal("5.000"));

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void apply_saida_throwsInsufficientStockExceptionWhenNotEnoughBalance() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("2.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.SAIDA, new BigDecimal("5.000")))
                .isInstanceOf(InsufficientStockException.class);
    }

    // EST-C009: AJUSTE deixou de ser delta e passou a ser o saldo contado na prateleira.

    @Test
    void apply_ajuste_substituiOSaldoParaCima() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("8.000"));

        assertThat(result.quantity()).as("vira o alvo, não 5 + 8").isEqualByComparingTo("8.000");
    }

    /** É o caso que o EST-C009 existia para resolver: antes só dava para baixar com SAIDA falsa. */
    @Test
    void apply_ajuste_substituiOSaldoParaBaixo() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("3.000"));

        assertThat(result.quantity()).isEqualByComparingTo("3.000");
    }

    @Test
    void apply_ajuste_paraZeroEhValido() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("10.000"), 0L);

        assertThat(balance.apply(MovementType.AJUSTE, BigDecimal.ZERO).quantity())
                .isEqualByComparingTo("0.000");
    }

    /** Baixar por AJUSTE nunca é "saldo insuficiente" — é substituição, não subtração. */
    @Test
    void apply_ajuste_abaixoDoSaldoAtual_naoLancaInsufficientStock() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("2.000"), 0L);

        assertThat(balance.apply(MovementType.AJUSTE, new BigDecimal("50.000")).quantity())
                .isEqualByComparingTo("50.000");
        assertThat(balance.apply(MovementType.AJUSTE, new BigDecimal("1.000")).quantity())
                .isEqualByComparingTo("1.000");
    }

    @Test
    void apply_ajuste_negativo_lancaIllegalArgument() {
        StockBalance balance = StockBalance.of(1L, "NARG-001", 1L, new BigDecimal("5.000"), 0L);

        assertThatThrownBy(() -> balance.apply(MovementType.AJUSTE, new BigDecimal("-1.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apply_ajuste_preservaIdSkuEVersion() {
        StockBalance balance = StockBalance.of(7L, "NARG-001", 3L, new BigDecimal("5.000"), 4L);

        StockBalance result = balance.apply(MovementType.AJUSTE, new BigDecimal("9.000"));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.warehouseId()).isEqualTo(3L);
        assertThat(result.version()).as("version preservado para o optimistic locking").isEqualTo(4L);
    }
}
