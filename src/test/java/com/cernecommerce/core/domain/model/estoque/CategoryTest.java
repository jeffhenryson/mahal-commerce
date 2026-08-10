package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {

    @Test
    void create_nasceAtivaSemDestaqueENoInicioDaOrdem() {
        Category category = Category.create("Narguilé");

        assertThat(category.id()).isNull();
        assertThat(category.name()).isEqualTo("Narguilé");
        assertThat(category.featured()).isFalse();
        assertThat(category.displayOrder()).isZero();
        assertThat(category.active()).isTrue();
    }

    @Test
    void nomeEAparadoNasPontas() {
        // O nome chega de texto livre digitado no formulário de produto.
        assertThat(Category.create("  Narguilé  ").name()).isEqualTo("Narguilé");
    }

    @Test
    void nomeVazioOuNuloERecusado() {
        assertThatThrownBy(() -> Category.create(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.create("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ordemNegativaERecusada() {
        assertThatThrownBy(() -> Category.create("X", false, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withDetails_nuloMantemCadaCampo() {
        Category original = Category.of(1L, "Narguilé", true, 3, true);

        Category soNome = original.withDetails("Essência", null, null);
        assertThat(soNome.name()).isEqualTo("Essência");
        assertThat(soNome.featured()).isTrue();
        assertThat(soNome.displayOrder()).isEqualTo(3);

        Category soDestaque = original.withDetails(null, false, null);
        assertThat(soDestaque.name()).isEqualTo("Narguilé");
        assertThat(soDestaque.featured()).isFalse();

        Category soOrdem = original.withDetails(null, null, 9);
        assertThat(soOrdem.displayOrder()).isEqualTo(9);
        assertThat(soOrdem.featured()).isTrue();
    }

    @Test
    void withDetails_naoMexeEmActive() {
        // Desativar tem endpoint próprio, para render evento de auditoria distinto de um rename.
        Category inativa = Category.of(1L, "Narguilé", false, 0, false);

        assertThat(inativa.withDetails("Outro Nome", true, 5).active()).isFalse();
    }

    @Test
    void withActive_alternaPreservandoOResto() {
        Category original = Category.of(1L, "Narguilé", true, 3, true);

        Category desativada = original.withActive(false);

        assertThat(desativada.active()).isFalse();
        assertThat(desativada.id()).isEqualTo(1L);
        assertThat(desativada.name()).isEqualTo("Narguilé");
        assertThat(desativada.featured()).isTrue();
        assertThat(desativada.displayOrder()).isEqualTo(3);
    }

    @Test
    void destaqueEOrdemSaoIndependentes() {
        // São coisas diferentes de propósito: destacar não pode exigir reordenar todo mundo.
        Category category = Category.create("Promoções", true, 0);

        assertThat(category.featured()).isTrue();
        assertThat(category.displayOrder()).isZero();
    }
}
