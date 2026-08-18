package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComandaItemTest {

    private static final Pricing PRICED = Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00"));

    @Test
    void fromCatalog_freezesPriceAndCostFromPricing() {
        ComandaItem item = ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE, PRICED, "Essência Menta");

        assertThat(item.id()).isNull();
        assertThat(item.sku()).isEqualTo("ESS-MENTA");
        assertThat(item.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(item.costPrice()).isEqualByComparingTo("10.00");
        assertThat(item.productName()).isEqualTo("Essência Menta");
        assertThat(item.addedAt()).isNotNull();
    }

    @Test
    void fromCatalog_rejectsProductWithoutPrice() {
        assertThatThrownBy(() -> ComandaItem.fromCatalog("SEM-PRECO", BigDecimal.ONE, Pricing.empty(), null))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void fromCatalog_rejectsNullPricingInsteadOfSellingForFree() {
        assertThatThrownBy(() -> ComandaItem.fromCatalog("SKU", BigDecimal.ONE, null, null))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void subtotal_isQuantityTimesUnitPrice() {
        ComandaItem item = ComandaItem.fromCatalog("ESS-MENTA", new BigDecimal("2"), PRICED, null);

        assertThat(item.subtotal()).isEqualByComparingTo("50.00");
    }

    @Test
    void rejectsBlankSkuAndNonPositiveQuantity() {
        assertThatThrownBy(() -> ComandaItem.of(1L, " ", BigDecimal.ONE, BigDecimal.TEN, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sku");
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ZERO, BigDecimal.TEN, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
    }

    @Test
    void rejectsMissingUnitPriceAndAddedAt() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("unitPrice");
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, BigDecimal.TEN, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("addedAt");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant addedAt = Instant.parse("2026-08-18T20:00:00Z");
        ComandaItem item = ComandaItem.of(9L, "ESS-MENTA", new BigDecimal("2"), new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", addedAt);

        assertThat(item.id()).isEqualTo(9L);
        assertThat(item.addedAt()).isEqualTo(addedAt);
    }
}
