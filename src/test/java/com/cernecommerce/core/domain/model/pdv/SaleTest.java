package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleTest {

    private List<SaleItem> oneItem() {
        return List.of(new SaleItem("NARG-001", new BigDecimal("2.000"), new BigDecimal("15.00")));
    }

    @Test
    void create_buildsSaleWithoutIdAndSumsTotalFromItems() {
        List<SaleItem> items = List.of(
                new SaleItem("NARG-001", new BigDecimal("2.000"), new BigDecimal("15.00")),
                new SaleItem("CARV-001", new BigDecimal("1.000"), new BigDecimal("10.00")));

        Sale sale = Sale.create(1L, "LOJA-01", items);

        assertThat(sale.id()).isNull();
        assertThat(sale.sessionId()).isEqualTo(1L);
        assertThat(sale.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(sale.items()).hasSize(2);
        assertThat(sale.totalAmount()).isEqualByComparingTo("40.00");
        assertThat(sale.soldAt()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant soldAt = Instant.parse("2026-07-01T10:00:00Z");
        Sale sale = Sale.of(9L, 1L, "LOJA-01", oneItem(), new BigDecimal("30.00"), soldAt);

        assertThat(sale.id()).isEqualTo(9L);
        assertThat(sale.totalAmount()).isEqualByComparingTo("30.00");
        assertThat(sale.soldAt()).isEqualTo(soldAt);
    }

    @Test
    void throwsWhenSessionIdIsNull() {
        assertThatThrownBy(() -> Sale.create(null, "LOJA-01", oneItem()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseCodeIsBlank() {
        assertThatThrownBy(() -> Sale.create(1L, "  ", oneItem()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenItemsIsEmpty() {
        assertThatThrownBy(() -> Sale.create(1L, "LOJA-01", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saleItem_throwsWhenQuantityIsZeroOrNegative() {
        assertThatThrownBy(() -> new SaleItem("NARG-001", BigDecimal.ZERO, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SaleItem("NARG-001", new BigDecimal("-1"), BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saleItem_throwsWhenUnitPriceIsNegative() {
        assertThatThrownBy(() -> new SaleItem("NARG-001", BigDecimal.ONE, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saleItem_subtotal_multipliesQuantityByUnitPrice() {
        SaleItem item = new SaleItem("NARG-001", new BigDecimal("3.000"), new BigDecimal("5.00"));

        assertThat(item.subtotal()).isEqualByComparingTo("15.00");
    }
}
