package com.cernecommerce.core.domain.model.compras;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsReceiptItemTest {

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> new GoodsReceiptItem("  ", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> new GoodsReceiptItem(null, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsZero() {
        assertThatThrownBy(() -> new GoodsReceiptItem("NARG-001", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsNegative() {
        assertThatThrownBy(() -> new GoodsReceiptItem("NARG-001", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenQuantityIsNull() {
        assertThatThrownBy(() -> new GoodsReceiptItem("NARG-001", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void construtorDeDoisArgumentos_deixaLoteExpiryEUnitCostNulos() {
        GoodsReceiptItem item = new GoodsReceiptItem("NARG-001", BigDecimal.TEN);

        assertThat(item.sku()).isEqualTo("NARG-001");
        assertThat(item.quantity()).isEqualByComparingTo("10");
        assertThat(item.lotCode()).isNull();
        assertThat(item.expiryDate()).isNull();
        assertThat(item.unitCost()).isNull();
    }

    @Test
    void construtorDeQuatroArgumentos_preservaLotCodeEExpiryDate_deixaUnitCostNulo() {
        GoodsReceiptItem item = new GoodsReceiptItem("ESSE-001", BigDecimal.TEN, "L1",
                LocalDate.parse("2027-01-01"));

        assertThat(item.sku()).isEqualTo("ESSE-001");
        assertThat(item.quantity()).isEqualByComparingTo("10");
        assertThat(item.lotCode()).isEqualTo("L1");
        assertThat(item.expiryDate()).isEqualTo(LocalDate.parse("2027-01-01"));
        assertThat(item.unitCost()).isNull();
    }

    @Test
    void construtorCompleto_preservaUnitCost() {
        GoodsReceiptItem item = new GoodsReceiptItem("ESSE-001", BigDecimal.TEN, "L1",
                LocalDate.parse("2027-01-01"), new BigDecimal("7.50"));

        assertThat(item.unitCost()).isEqualByComparingTo("7.50");
    }
}
