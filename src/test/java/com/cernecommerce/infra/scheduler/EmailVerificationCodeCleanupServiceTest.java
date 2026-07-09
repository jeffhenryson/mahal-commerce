package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.notification.EmailVerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationCodeCleanupServiceTest {

    @Mock EmailVerificationCodeRepository repository;

    @Test
    void cleanup_delegates_deleteExpiredBefore() {
        EmailVerificationCodeCleanupService service = new EmailVerificationCodeCleanupService(repository);

        service.cleanup();

        verify(repository).deleteExpiredBefore(any());
    }
}
