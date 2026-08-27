package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
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
    // ── Sessão de narguilé (PDV-F010) ────────────────────────────────────────────────────────

    /**
     * O ponto que a feature inteira gira em torno: em {@code OPEN_ROSH} o preço vem do
     * {@code openRoshPrice} do produto <b>pai</b>, e não do {@code pricing} do SKU da variação do
     * sabor — que aqui cobraria 25 no lugar de 60.
     */
    @Test
    void forSession_takesUnitPriceFromTheCallerButCostFromTheCatalog() {
        ComandaItem item = ComandaItem.forSession("SESS-BLUE", BigDecimal.ONE, new BigDecimal("60.00"),
                PRICED, "Sessão Blueberry", ConsumptionMode.OPEN_ROSH, false, null);

        assertThat(item.unitPrice()).isEqualByComparingTo("60.00");
        assertThat(item.costPrice()).isEqualByComparingTo("10.00");
        assertThat(item.mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
        assertThat(item.courtesy()).isFalse();
    }

    /**
     * Cortesia não é linha grátis para a contabilidade: o custo é congelado normalmente, e é ele
     * que faz a margem do pedido mostrar o prejuízo real da promo e do open rosh. Custo nulo ou
     * zerado aqui mentiria sobre a pergunta de negócio por trás da feature.
     */
    @Test
    void forSession_courtesyIsFreeForTheCustomerButNotForTheMargin() {
        ComandaItem cortesia = ComandaItem.forSession("SESS-UVA", BigDecimal.ONE, BigDecimal.ZERO,
                PRICED, "Sessão Uva", ConsumptionMode.SABOR_EXTRA, true, 7L);

        assertThat(cortesia.unitPrice()).isEqualByComparingTo("0");
        assertThat(cortesia.subtotal()).isEqualByComparingTo("0.00");
        assertThat(cortesia.costPrice()).isEqualByComparingTo("10.00");
        assertThat(cortesia.courtesy()).isTrue();
        assertThat(cortesia.linkedItemId()).isEqualTo(7L);
    }

    /** A checagem de preço vale mesmo em cortesia — é justamente para não gravar custo nulo. */
    @Test
    void forSession_rejectsUnpricedProductEvenForACourtesyLine() {
        assertThatThrownBy(() -> ComandaItem.forSession("SEM-PRECO", BigDecimal.ONE, BigDecimal.ZERO,
                Pricing.empty(), null, ConsumptionMode.TROCA, true, 7L))
                .isInstanceOf(ProductNotPricedException.class);
    }

    @Test
    void courtesyMustCostZero() {
        // Espelha o CHECK ck_comanda_item_courtesy_is_free: cortesia é preço zero por definição.
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, new BigDecimal("25.00"), null,
                null, Instant.now(), ConsumptionMode.SABOR_EXTRA, true, 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cortesia");
    }

    @Test
    void linkedItemIdOnlyMakesSenseForSaborExtraAndTroca() {
        assertThatThrownBy(() -> ComandaItem.of(1L, "SKU", BigDecimal.ONE, new BigDecimal("25.00"), null,
                null, Instant.now(), ConsumptionMode.OPEN_ROSH, false, 7L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("linkedItemId");
    }

    /** Linha anterior a PDV-F010 lê como {@code NORMAL}, nunca com modo nulo. */
    @Test
    void of_legacyRowReadsAsNormal() {
        ComandaItem legado = ComandaItem.of(9L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now(), null, false, null);

        assertThat(legado.mode()).isEqualTo(ConsumptionMode.NORMAL);
    }
}
