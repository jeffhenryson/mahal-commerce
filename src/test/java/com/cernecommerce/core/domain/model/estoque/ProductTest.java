package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private Product product() {
        return Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true,
                List.of(ProductVariant.of(2L, "NARG-001-M", List.of(new ProductAttribute("sabor", "menta")), true)));
    }

    @Test
    void create_buildsActiveProductWithoutId() {
        Product created = Product.create("NARG-001", "Narguile Aladin", "narguile", List.of());

        assertThat(created.id()).isNull();
        assertThat(created.active()).isTrue();
        assertThat(created.variants()).isEmpty();
    }

    @Test
    void nullVariantsBecomesEmptyList() {
        assertThat(Product.create("NARG-001", "Narguile", "cat", null).variants()).isEmpty();
    }

    @Test
    void variantsAreDefensivelyCopied() {
        List<ProductVariant> mutable = new ArrayList<>();
        mutable.add(ProductVariant.of(2L, "NARG-001-M", List.of(), true));

        Product created = Product.create("NARG-001", "Narguile", "cat", mutable);
        mutable.clear();

        assertThat(created.variants()).hasSize(1);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> Product.create("  ", "Narguile", "cat", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNameIsBlank() {
        assertThatThrownBy(() -> Product.create("NARG-001", "", "cat", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // EST-F018 — alteração parcial e desativação

    @Test
    void withDetails_nullMantemOCampo() {
        Product original = product();

        Product soNome = original.withDetails("Narguilé Aladin 2.0", null);
        assertThat(soNome.name()).isEqualTo("Narguilé Aladin 2.0");
        assertThat(soNome.category()).isEqualTo("narguile");

        Product soCategoria = original.withDetails(null, "narguile-premium");
        assertThat(soCategoria.name()).isEqualTo("Narguile Aladin");
        assertThat(soCategoria.category()).isEqualTo("narguile-premium");
    }

    @Test
    void withDetails_preservaIdSkuActiveEVariacoes() {
        Product original = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", false,
                List.of(ProductVariant.of(2L, "NARG-001-M", List.of(), true)));

        Product updated = original.withDetails("Outro nome", "outra-categoria");

        assertThat(updated.id()).isEqualTo(1L);
        assertThat(updated.sku()).as("sku é referenciado como texto livre pelas tabelas de estoque").isEqualTo("NARG-001");
        assertThat(updated.active()).as("ativação tem endpoint próprio").isFalse();
        assertThat(updated.variants()).extracting(ProductVariant::sku).containsExactly("NARG-001-M");
    }

    /**
     * Limitação conhecida da semântica "null = manter": não há como limpar a categoria por este
     * caminho, só trocá-la.
     */
    @Test
    void withDetails_naoConsegueLimparACategoria() {
        assertThat(product().withDetails(null, null).category()).isEqualTo("narguile");
    }

    @Test
    void withDetails_naoDeixaBurlarOsInvariantes() {
        assertThatThrownBy(() -> product().withDetails("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withActive_alternaPreservandoORestante() {
        Product desativado = product().withActive(false);

        assertThat(desativado.active()).isFalse();
        assertThat(desativado.id()).isEqualTo(1L);
        assertThat(desativado.sku()).isEqualTo("NARG-001");
        assertThat(desativado.name()).isEqualTo("Narguile Aladin");
        assertThat(desativado.category()).isEqualTo("narguile");
        assertThat(desativado.variants()).hasSize(1);
        assertThat(desativado.withActive(true).active()).isTrue();
    }
}
