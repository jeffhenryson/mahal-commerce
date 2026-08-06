package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductAttributeTest {

    @Test
    void create_buildsWithTypeAndValue() {
        ProductAttribute attribute = new ProductAttribute("sabor", "menta");

        assertThat(attribute.type()).isEqualTo("sabor");
        assertThat(attribute.value()).isEqualTo("menta");
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> new ProductAttribute(null, "menta"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
    }

    @Test
    void rejectsEmptyType() {
        assertThatThrownBy(() -> new ProductAttribute("", "menta"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(() -> new ProductAttribute("   ", "menta"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type");
    }

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> new ProductAttribute("sabor", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("value");
    }

    @Test
    void rejectsEmptyValue() {
        assertThatThrownBy(() -> new ProductAttribute("sabor", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("value");
    }

    @Test
    void rejectsBlankValue() {
        assertThatThrownBy(() -> new ProductAttribute("sabor", "   "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("value");
    }
}
