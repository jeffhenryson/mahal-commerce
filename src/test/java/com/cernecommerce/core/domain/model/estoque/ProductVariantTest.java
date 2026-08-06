package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

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
}
