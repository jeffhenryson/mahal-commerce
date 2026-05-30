package com.securityspring.infra.scheduler;

import com.securityspring.core.ports.out.notification.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenCleanupServiceTest {

    @Mock PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void cleanup_delegates_deleteExpiredBefore() {
        PasswordResetTokenCleanupService service = new PasswordResetTokenCleanupService(passwordResetTokenRepository);

        service.cleanup();

        verify(passwordResetTokenRepository).deleteExpiredBefore(any());
    }
}
