package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.twofa.DevChallengeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DevChallengeCleanupServiceTest {

    @Mock DevChallengeRepository devChallengeRepository;

    @Test
    void cleanup_delegates_deleteExpiredBefore() {
        DevChallengeCleanupService service = new DevChallengeCleanupService(devChallengeRepository);

        service.cleanup();

        verify(devChallengeRepository).deleteExpiredBefore(any());
    }
}
