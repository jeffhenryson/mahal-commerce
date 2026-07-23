package com.cernecommerce.core.domain.model.compras;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoodsReceiptTest {

    private List<GoodsReceiptItem> oneItem() {
        return List.of(new GoodsReceiptItem("NARG-001", new BigDecimal("10.000")));
    }

    @Test
    void create_buildsReceiptWithoutIdAndCurrentTimestamp() {
        GoodsReceipt receipt = GoodsReceipt.create(1L, "LOJA-01", oneItem(), "gerente");

        assertThat(receipt.id()).isNull();
        assertThat(receipt.supplierId()).isEqualTo(1L);
        assertThat(receipt.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(receipt.items()).hasSize(1);
        assertThat(receipt.username()).isEqualTo("gerente");
        assertThat(receipt.receivedAt()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant receivedAt = Instant.parse("2026-07-01T10:00:00Z");
        GoodsReceipt receipt = GoodsReceipt.of(9L, 1L, "LOJA-01", oneItem(), "gerente", receivedAt);

        assertThat(receipt.id()).isEqualTo(9L);
        assertThat(receipt.receivedAt()).isEqualTo(receivedAt);
    }

    @Test
    void throwsWhenSupplierIdIsNull() {
        assertThatThrownBy(() -> GoodsReceipt.create(null, "LOJA-01", oneItem(), "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenWarehouseCodeIsBlank() {
        assertThatThrownBy(() -> GoodsReceipt.create(1L, "  ", oneItem(), "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenItemsIsEmpty() {
        assertThatThrownBy(() -> GoodsReceipt.create(1L, "LOJA-01", List.of(), "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenUsernameIsBlank() {
        assertThatThrownBy(() -> GoodsReceipt.create(1L, "LOJA-01", oneItem(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemThrowsWhenSkuIsBlank() {
        assertThatThrownBy(() -> new GoodsReceiptItem("  ", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemThrowsWhenQuantityIsZeroOrNegative() {
        assertThatThrownBy(() -> new GoodsReceiptItem("NARG-001", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GoodsReceiptItem("NARG-001", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
