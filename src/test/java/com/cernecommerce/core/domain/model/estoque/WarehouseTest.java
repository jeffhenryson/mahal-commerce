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
}
