package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignLogEntryTest {

    @Test
    void create_buildsEntryPendingIntegrationWithoutConversao() {
        CampaignLogEntry entry = CampaignLogEntry.create(1L, 10L);

        assertThat(entry.id()).isNull();
        assertThat(entry.automationId()).isEqualTo(1L);
        assertThat(entry.customerId()).isEqualTo(10L);
        assertThat(entry.status()).isEqualTo(CampaignDispatchStatus.PENDENTE_INTEGRACAO);
        assertThat(entry.disparadoEm()).isNotNull();
        assertThat(entry.convertidoEm()).isNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant disparadoEm = Instant.parse("2026-01-01T00:00:00Z");
        CampaignLogEntry entry = CampaignLogEntry.of(5L, 1L, 10L, CampaignDispatchStatus.PENDENTE_INTEGRACAO,
                disparadoEm, null, null);

        assertThat(entry.id()).isEqualTo(5L);
        assertThat(entry.disparadoEm()).isEqualTo(disparadoEm);
    }

    @Test
    void throwsWhenAutomationIdOrCustomerIdIsNull() {
        assertThatThrownBy(() -> CampaignLogEntry.create(null, 10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampaignLogEntry.create(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enviado_buildsEntryWithEnviadoStatusAndNoErro() {
        CampaignLogEntry entry = CampaignLogEntry.enviado(1L, 10L);

        assertThat(entry.status()).isEqualTo(CampaignDispatchStatus.ENVIADO);
        assertThat(entry.erroDetalhe()).isNull();
    }

    @Test
    void falha_buildsEntryWithFalhaStatusAndErroDetalhe() {
        CampaignLogEntry entry = CampaignLogEntry.falha(1L, 10L, "timeout ao conectar");

        assertThat(entry.status()).isEqualTo(CampaignDispatchStatus.FALHA);
        assertThat(entry.erroDetalhe()).isEqualTo("timeout ao conectar");
    }
}
