package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReorderPointTest {

    @Test
    void create_buildsReorderPointWithoutId() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.id()).isNull();
        assertThat(reorderPoint.sku()).isEqualTo("ESS-001");
        assertThat(reorderPoint.warehouseId()).isEqualTo(1L);
        assertThat(reorderPoint.minQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        ReorderPoint reorderPoint = ReorderPoint.of(9L, "ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.id()).isEqualTo(9L);
        assertThat(reorderPoint.sku()).isEqualTo("ESS-001");
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> ReorderPoint.create("  ", 1L, new BigDecimal("10.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> ReorderPoint.create(null, 1L, new BigDecimal("10.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseIdIsNull() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", null, new BigDecimal("10.000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenMinQuantityIsNull() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", 1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenMinQuantityIsNegative() {
        assertThatThrownBy(() -> ReorderPoint.create("ESS-001", 1L, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_aceitaMinQuantityZero() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, BigDecimal.ZERO);

        assertThat(reorderPoint.minQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void isBelow_trueQuandoQuantidadeEstaAbaixoDoPonto() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.isBelow(new BigDecimal("9.999"))).isTrue();
    }

    @Test
    void isBelow_falseQuandoQuantidadeEstaIgualAoPonto() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.isBelow(new BigDecimal("10.000"))).isFalse();
    }

    @Test
    void isBelow_falseQuandoQuantidadeEstaAcimaDoPonto() {
        ReorderPoint reorderPoint = ReorderPoint.create("ESS-001", 1L, new BigDecimal("10.000"));

        assertThat(reorderPoint.isBelow(new BigDecimal("10.001"))).isFalse();
    }
}
