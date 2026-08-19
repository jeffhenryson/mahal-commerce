package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrandTest {

    @Test
    void create_nasceAtiva() {
        Brand brand = Brand.create("Zomo");

        assertThat(brand.id()).isNull();
        assertThat(brand.name()).isEqualTo("Zomo");
        assertThat(brand.active()).isTrue();
    }

    @Test
    void nomeEAparadoNasPontas() {
        assertThat(Brand.create("  Zomo  ").name()).isEqualTo("Zomo");
    }

    @Test
    void nomeVazioOuNuloERecusado() {
        assertThatThrownBy(() -> Brand.create(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Brand.create("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withDetails_nuloMantemONome() {
        Brand original = Brand.of(1L, "Zomo", true);

        assertThat(original.withDetails(null).name()).isEqualTo("Zomo");
        assertThat(original.withDetails("Alfaraby").name()).isEqualTo("Alfaraby");
    }

    @Test
    void withDetails_naoMexeEmActive() {
        // Desativar tem endpoint próprio, mesma régua de Category — evento de auditoria distinto
        // de uma correção de nome.
        Brand inativa = Brand.of(1L, "Zomo", false);

        assertThat(inativa.withDetails("Novo Nome").active()).isFalse();
    }

    @Test
    void withActive_alternaPreservandoOResto() {
        Brand original = Brand.of(1L, "Zomo", true);

        Brand desativada = original.withActive(false);

        assertThat(desativada.active()).isFalse();
        assertThat(desativada.id()).isEqualTo(1L);
        assertThat(desativada.name()).isEqualTo("Zomo");
    }
}
