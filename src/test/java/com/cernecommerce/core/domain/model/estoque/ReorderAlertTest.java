package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReorderAlertTest {

    @Test
    void create_buildsWithSkuQuantityAndMinQuantity() {
        ReorderAlert alert = new ReorderAlert("NARG-001", new BigDecimal("3.000"), new BigDecimal("10.000"));

        assertThat(alert.sku()).isEqualTo("NARG-001");
        assertThat(alert.quantity()).isEqualByComparingTo("3.000");
        assertThat(alert.minQuantity()).isEqualByComparingTo("10.000");
    }

    @Test
    void rejectsNullSku() {
        assertThatThrownBy(() -> new ReorderAlert(null, BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sku");
    }

    @Test
    void rejectsEmptySku() {
        assertThatThrownBy(() -> new ReorderAlert("", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sku");
    }

    @Test
    void rejectsBlankSku() {
        assertThatThrownBy(() -> new ReorderAlert("   ", BigDecimal.ONE, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sku");
    }

    @Test
    void rejectsNullQuantity() {
        assertThatThrownBy(() -> new ReorderAlert("NARG-001", null, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
    }

    @Test
    void rejectsNullMinQuantity() {
        assertThatThrownBy(() -> new ReorderAlert("NARG-001", BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minQuantity");
    }
}
