package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseTest {

    @Test
    void create_buildsActiveWarehouseWithoutId() {
        Warehouse warehouse = Warehouse.create("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA);

        assertThat(warehouse.id()).isNull();
        assertThat(warehouse.code()).isEqualTo("LOJA-01");
        assertThat(warehouse.type()).isEqualTo(WarehouseType.LOJA_FISICA);
        assertThat(warehouse.active()).isTrue();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Warehouse warehouse = Warehouse.of(1L, "ECOMMERCE", "Depósito E-commerce", WarehouseType.ECOMMERCE, false);

        assertThat(warehouse.id()).isEqualTo(1L);
        assertThat(warehouse.active()).isFalse();
    }

    @Test
    void create_throwsWhenCodeIsBlank() {
        assertThatThrownBy(() -> Warehouse.create("  ", "Loja Centro", WarehouseType.LOJA_FISICA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throwsWhenNameIsBlank() {
        assertThatThrownBy(() -> Warehouse.create("LOJA-01", "", WarehouseType.LOJA_FISICA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throwsWhenTypeIsNull() {
        assertThatThrownBy(() -> Warehouse.create("LOJA-01", "Loja Centro", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // EST-F018 — alteração parcial e desativação

    @Test
    void withDetails_nullMantemOCampo() {
        Warehouse original = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

        Warehouse soNome = original.withDetails("Loja Reformada", null);
        assertThat(soNome.name()).isEqualTo("Loja Reformada");
        assertThat(soNome.type()).isEqualTo(WarehouseType.LOJA_FISICA);

        Warehouse soTipo = original.withDetails(null, WarehouseType.ECOMMERCE);
        assertThat(soTipo.name()).isEqualTo("Loja Centro");
        assertThat(soTipo.type()).isEqualTo(WarehouseType.ECOMMERCE);
    }

    @Test
    void withDetails_preservaIdCodeEActive() {
        Warehouse original = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, false);

        Warehouse updated = original.withDetails("Outro nome", WarehouseType.ECOMMERCE);

        assertThat(updated.id()).isEqualTo(1L);
        assertThat(updated.code()).as("code é a identidade pública, não muda por PATCH").isEqualTo("LOJA-01");
        assertThat(updated.active()).as("ativação tem endpoint próprio").isFalse();
    }

    @Test
    void withDetails_naoDeixaBurlarOsInvariantes() {
        Warehouse original = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

        assertThatThrownBy(() -> original.withDetails("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withActive_alternaPreservandoORestante() {
        Warehouse original = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

        Warehouse desativado = original.withActive(false);

        assertThat(desativado.active()).isFalse();
        assertThat(desativado.id()).isEqualTo(1L);
        assertThat(desativado.code()).isEqualTo("LOJA-01");
        assertThat(desativado.name()).isEqualTo("Loja Centro");
        assertThat(desativado.type()).isEqualTo(WarehouseType.LOJA_FISICA);
        assertThat(desativado.withActive(true).active()).isTrue();
    }
}
