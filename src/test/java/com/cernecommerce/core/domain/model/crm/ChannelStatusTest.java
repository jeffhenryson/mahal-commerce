package com.cernecommerce.core.domain.model.crm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelStatusTest {

    @Test
    void of_buildsConnectedStatus() {
        ChannelStatus status = ChannelStatus.of(ChannelType.EMAIL, true, "MAILPIT", "Conectado ao Mailpit");

        assertThat(status.canal()).isEqualTo(ChannelType.EMAIL);
        assertThat(status.conectado()).isTrue();
        assertThat(status.provedor()).isEqualTo("MAILPIT");
        assertThat(status.detalhe()).isEqualTo("Conectado ao Mailpit");
    }

    @Test
    void of_buildsDisconnectedStatusWithoutProvider() {
        ChannelStatus status = ChannelStatus.of(ChannelType.WHATSAPP, false, null,
                "Integração de WhatsApp ainda não implementada");

        assertThat(status.conectado()).isFalse();
        assertThat(status.provedor()).isNull();
    }

    @Test
    void constructor_rejectsNullCanal() {
        assertThatThrownBy(() -> new ChannelStatus(null, true, "MAILPIT", "ok"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
