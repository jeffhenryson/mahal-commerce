package com.securityspring.infra.scheduler;

import com.securityspring.core.ports.out.RefreshTokenPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupServiceTest {

    @Mock
    RefreshTokenPort refreshTokenPort;

    @Test
    void cleanup_delegates_to_port_deleteExpiredAndRevoked() {
        RefreshTokenCleanupService service = new RefreshTokenCleanupService(refreshTokenPort);

        service.cleanup();

        verify(refreshTokenPort).deleteExpiredAndRevoked();
    }
}
