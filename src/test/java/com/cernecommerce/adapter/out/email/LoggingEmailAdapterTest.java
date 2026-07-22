package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmailAdapterTest {

    @Test
    void channelStatus_reportsDisconnectedLogOnlyProvider() {
        EmailChannelStatus status = new LoggingEmailAdapter().channelStatus();

        assertThat(status.conectado()).isFalse();
        assertThat(status.provedor()).isEqualTo("LOG");
    }
}
