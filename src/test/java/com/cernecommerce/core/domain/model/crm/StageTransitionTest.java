package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageTransitionTest {

    @Test
    void create_buildsTransitionWithoutIdAndWithTransicionadoEmSet() {
        StageTransition t = StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "gerente");

        assertThat(t.id()).isNull();
        assertThat(t.customerId()).isEqualTo(1L);
        assertThat(t.de()).isEqualTo(CustomerStage.NOVO_LEAD);
        assertThat(t.para()).isEqualTo(CustomerStage.EM_ATENDIMENTO);
        assertThat(t.autor()).isEqualTo("gerente");
        assertThat(t.transicionadoEm()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant when = Instant.parse("2026-01-01T00:00:00Z");
        StageTransition t = StageTransition.of(5L, 1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO,
                "gerente", when);

        assertThat(t.id()).isEqualTo(5L);
        assertThat(t.transicionadoEm()).isEqualTo(when);
    }

    @Test
    void throwsWhenCustomerIdIsNull() {
        assertThatThrownBy(() -> StageTransition.create(null, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenDeOrParaIsNull() {
        assertThatThrownBy(() -> StageTransition.create(1L, null, CustomerStage.EM_ATENDIMENTO, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StageTransition.create(1L, CustomerStage.NOVO_LEAD, null, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenDeEqualsParaTransitioningToSameStage() {
        assertThatThrownBy(() -> StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.NOVO_LEAD, "gerente"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenAutorIsBlank() {
        assertThatThrownBy(() -> StageTransition.create(1L, CustomerStage.NOVO_LEAD, CustomerStage.EM_ATENDIMENTO, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
