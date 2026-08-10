package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductVariantTest {

    @Test
    void create_buildsActiveVariantWithoutId() {
        List<ProductAttribute> attributes = List.of(new ProductAttribute("sabor", "menta"));

        ProductVariant variant = ProductVariant.create("ESS-001-MENTA", attributes);

        assertThat(variant.id()).isNull();
        assertThat(variant.sku()).isEqualTo("ESS-001-MENTA");
        assertThat(variant.attributes()).containsExactly(new ProductAttribute("sabor", "menta"));
        assertThat(variant.active()).isTrue();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        ProductVariant variant = ProductVariant.of(1L, "ESS-001-MENTA", List.of(), false);

        assertThat(variant.id()).isEqualTo(1L);
        assertThat(variant.active()).isFalse();
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> ProductVariant.create("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenSkuIsNull() {
        assertThatThrownBy(() -> ProductVariant.create(null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void attributesNuloViraListaVazia() {
        ProductVariant variant = ProductVariant.create("ESS-001", null);

        assertThat(variant.attributes()).isEmpty();
    }

    @Test
    void attributesFornecidosSaoPreservados() {
        List<ProductAttribute> attributes = List.of(
                new ProductAttribute("sabor", "menta"),
                new ProductAttribute("tamanho", "P"));

        ProductVariant variant = ProductVariant.create("ESS-001", attributes);

        assertThat(variant.attributes()).containsExactly(
                new ProductAttribute("sabor", "menta"),
                new ProductAttribute("tamanho", "P"));
    }

    @Test
    void attributesSaoImutaveis() {
        java.util.ArrayList<ProductAttribute> mutable = new java.util.ArrayList<>();
        mutable.add(new ProductAttribute("sabor", "menta"));

        ProductVariant variant = ProductVariant.create("ESS-001", mutable);
        mutable.add(new ProductAttribute("tamanho", "P"));

        assertThat(variant.attributes()).hasSize(1);
        assertThatThrownBy(() -> variant.attributes().add(new ProductAttribute("cor", "preto")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── Preço próprio da variação (EST-F020) ─────────────────────────────────

    @Test
    void variacaoNasceSemPrecoProprioEHerdaDoPai() {
        // Retrocompatibilidade: as sobrecargas antigas continuam significando "herda".
        assertThat(ProductVariant.create("V-1", List.of()).pricing()).isNull();
        assertThat(ProductVariant.of(1L, "V-1", List.of(), true).pricing()).isNull();
        assertThat(ProductVariant.create("V-1", List.of()).hasOwnPricing()).isFalse();
    }

    @Test
    void hasOwnPricing_verdadeiroQuandoHaPrecoPreenchido() {
        ProductVariant variant = ProductVariant.create("V-1", List.of(),
                Pricing.of(null, null, new BigDecimal("99.90")));

        assertThat(variant.hasOwnPricing()).isTrue();
        assertThat(variant.pricing().salePrice()).isEqualByComparingTo("99.90");
    }

    @Test
    void pricingVazioNaoContaComoPrecoProprio() {
        // Um Pricing sem nenhum campo não tem o que sobrescrever; tratá-lo como override
        // apagaria o preço do pai na leitura.
        assertThat(ProductVariant.create("V-1", List.of(), Pricing.empty()).hasOwnPricing()).isFalse();
    }

    @Test
    void withPricing_defineERemoveOPrecoProprio() {
        ProductVariant comPreco = ProductVariant.create("V-1", List.of())
                .withPricing(Pricing.of(null, null, new BigDecimal("50.00")));
        assertThat(comPreco.hasOwnPricing()).isTrue();

        // null volta a herdar do pai.
        assertThat(comPreco.withPricing(null).pricing()).isNull();
    }

    @Test
    void withPricing_preservaOResto() {
        ProductVariant variant = ProductVariant.of(7L, "V-1",
                List.of(new ProductAttribute("Sabor", "Menta")), false);

        ProductVariant updated = variant.withPricing(Pricing.of(null, null, new BigDecimal("10.00")));

        assertThat(updated.id()).isEqualTo(7L);
        assertThat(updated.sku()).isEqualTo("V-1");
        assertThat(updated.active()).isFalse();
        assertThat(updated.attributes()).hasSize(1);
    }
}
