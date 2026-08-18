package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignAutomationTest {

    @Test
    void create_buildsActiveAutomationWithoutId() {
        CampaignAutomation automation = CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}, seu saldo e {saldo}");

        assertThat(automation.id()).isNull();
        assertThat(automation.nome()).isEqualTo("Boas-vindas");
        assertThat(automation.gatilho()).isEqualTo(CampaignTrigger.MANUAL);
        assertThat(automation.segmentoAlvo()).isEqualTo(CustomerStage.NOVO_LEAD);
        assertThat(automation.canal()).isEqualTo(CampaignChannel.EMAIL);
        assertThat(automation.ativa()).isTrue();
        assertThat(automation.criadoEm()).isNotNull();
    }

    @Test
    void of_reconstitutesFromPersistence() {
        Instant criadoEm = Instant.parse("2026-01-01T00:00:00Z");
        CampaignAutomation automation = CampaignAutomation.of(1L, "Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}", false, criadoEm, null, Map.of());

        assertThat(automation.id()).isEqualTo(1L);
        assertThat(automation.ativa()).isFalse();
        assertThat(automation.criadoEm()).isEqualTo(criadoEm);
        assertThat(automation.hasWebhook()).isFalse();
    }

    @Test
    void withDetails_returnsNewInstanceWithUpdatedFieldsAndWebhook() {
        CampaignAutomation automation = CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}");

        CampaignAutomation updated = automation.withDetails("Novo nome", CampaignTrigger.MANUAL,
                CustomerStage.QUALIFICADO, CampaignChannel.WHATSAPP, "Novo template",
                "https://n8n.example.com/webhook/abc", Map.of("Authorization", "Bearer token"));

        assertThat(updated.nome()).isEqualTo("Novo nome");
        assertThat(updated.segmentoAlvo()).isEqualTo(CustomerStage.QUALIFICADO);
        assertThat(updated.canal()).isEqualTo(CampaignChannel.WHATSAPP);
        assertThat(updated.template()).isEqualTo("Novo template");
        assertThat(updated.webhookUrl()).isEqualTo("https://n8n.example.com/webhook/abc");
        assertThat(updated.webhookHeaders()).containsEntry("Authorization", "Bearer token");
        assertThat(updated.hasWebhook()).isTrue();
        assertThat(automation.hasWebhook()).isFalse();
    }

    @Test
    void throwsWhenWebhookUrlIsMalformed() {
        assertThatThrownBy(() -> CampaignAutomation.of(1L, "Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}", true, Instant.now(),
                "http://[invalid", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withAtiva_returnsNewInstanceWithUpdatedFlag() {
        CampaignAutomation automation = CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, "Ola {nome}");

        CampaignAutomation deactivated = automation.withAtiva(false);

        assertThat(deactivated.ativa()).isFalse();
        assertThat(automation.ativa()).isTrue();
    }

    @Test
    void throwsWhenNomeIsBlank() {
        assertThatThrownBy(() -> CampaignAutomation.create(" ", CampaignTrigger.MANUAL, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenTemplateIsBlank() {
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, CampaignChannel.EMAIL, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenGatilhoSegmentoOuCanalSaoNulos() {
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", null, CustomerStage.NOVO_LEAD,
                CampaignChannel.EMAIL, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL, null,
                CampaignChannel.EMAIL, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CampaignAutomation.create("Boas-vindas", CampaignTrigger.MANUAL,
                CustomerStage.NOVO_LEAD, null, "Ola {nome}"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
