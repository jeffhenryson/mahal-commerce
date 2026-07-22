package com.cernecommerce.core.domain.model.notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailChannelStatusTest {

    @Test
    void of_buildsStatusWithAllFields() {
        EmailChannelStatus status = EmailChannelStatus.of(true, "RESEND", "Conectado à API Resend");

        assertThat(status.conectado()).isTrue();
        assertThat(status.provedor()).isEqualTo("RESEND");
        assertThat(status.detalhe()).isEqualTo("Conectado à API Resend");
    }
}
