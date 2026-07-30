package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KitComponentTest {

    @Test
    void create_buildsWithoutId() {
        KitComponent component = KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2"));

        assertThat(component.id()).isNull();
        assertThat(component.kitSku()).isEqualTo("KIT-001");
        assertThat(component.componentSku()).isEqualTo("CARV-001");
        assertThat(component.quantity()).isEqualByComparingTo("2");
    }

    @Test
    void of_reconstitutesFromPersistence() {
        KitComponent component = KitComponent.of(9L, "KIT-001", "CARV-001", new BigDecimal("2"));

        assertThat(component.id()).isEqualTo(9L);
    }

    @Test
    void rejectsBlankKitSku() {
        assertThatThrownBy(() -> KitComponent.create(" ", "CARV-001", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kitSku");
    }

    @Test
    void rejectsBlankComponentSku() {
        assertThatThrownBy(() -> KitComponent.create("KIT-001", " ", BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("componentSku");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> KitComponent.create("KIT-001", "CARV-001", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
        assertThatThrownBy(() -> KitComponent.create("KIT-001", "CARV-001", new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("quantity");
    }

    /**
     * Autorreferência (kitSku == componentSku) NÃO é checada aqui de propósito: viraria só um
     * IllegalArgumentException/400 genérico, mas o desenho pede um 409 KIT_SELF_REFERENCE
     * distinguível — essa regra mora em EstoqueService.defineKitRecipe.
     */
    @Test
    void doesNotRejectSelfReferenceHere() {
        KitComponent component = KitComponent.create("KIT-001", "KIT-001", BigDecimal.ONE);

        assertThat(component.componentSku()).isEqualTo(component.kitSku());
    }
}
