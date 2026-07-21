package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TagTest {

    @Test
    void create_buildsTagWithoutId() {
        Tag tag = Tag.create("VIP");

        assertThat(tag.id()).isNull();
        assertThat(tag.nome()).isEqualTo("VIP");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Tag tag = Tag.of(1L, "VIP");

        assertThat(tag.id()).isEqualTo(1L);
        assertThat(tag.nome()).isEqualTo("VIP");
    }

    @Test
    void throwsWhenNomeIsBlank() {
        assertThatThrownBy(() -> Tag.create("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
