package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.twofa.TotpChallengeTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TotpChallengeCleanupServiceTest {

    @Mock TotpChallengeTokenRepository totpChallengeTokenRepository;

    @Test
    void cleanup_delegates_deleteExpiredBefore() {
        TotpChallengeCleanupService service = new TotpChallengeCleanupService(totpChallengeTokenRepository);

        service.cleanup();

        verify(totpChallengeTokenRepository).deleteExpiredBefore(any());
    }
}
