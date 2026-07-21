package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerNoteTest {

    @Test
    void create_buildsNoteWithoutIdAndWithCriadoEmSet() {
        CustomerNote note = CustomerNote.create(1L, "gerente", "Cliente prefere contato por WhatsApp");

        assertThat(note.id()).isNull();
        assertThat(note.customerId()).isEqualTo(1L);
        assertThat(note.autor()).isEqualTo("gerente");
        assertThat(note.texto()).isEqualTo("Cliente prefere contato por WhatsApp");
        assertThat(note.criadoEm()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant criadoEm = Instant.parse("2026-01-01T00:00:00Z");
        CustomerNote note = CustomerNote.of(5L, 1L, "gerente", "Nota", criadoEm);

        assertThat(note.id()).isEqualTo(5L);
        assertThat(note.criadoEm()).isEqualTo(criadoEm);
    }

    @Test
    void throwsWhenCustomerIdIsNull() {
        assertThatThrownBy(() -> CustomerNote.create(null, "gerente", "Nota"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenAutorIsBlank() {
        assertThatThrownBy(() -> CustomerNote.create(1L, "  ", "Nota"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenTextoIsBlank() {
        assertThatThrownBy(() -> CustomerNote.create(1L, "gerente", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
