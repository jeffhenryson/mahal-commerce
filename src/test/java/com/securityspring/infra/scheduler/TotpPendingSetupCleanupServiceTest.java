package com.securityspring.infra.scheduler;

import com.securityspring.core.ports.out.twofa.TotpConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TotpPendingSetupCleanupServiceTest {

    @Mock TotpConfigRepository totpConfigRepository;

    @Test
    void cleanup_delegates_deleteUnconfirmedBefore_with_ttl_offset() {
        TotpPendingSetupCleanupService service = new TotpPendingSetupCleanupService(totpConfigRepository, 24);

        Instant before = Instant.now();
        service.cleanup();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(totpConfigRepository).deleteUnconfirmedBefore(captor.capture());

        Instant cutoff = captor.getValue();
        assertThat(cutoff).isBefore(before.minusSeconds(24 * 3600 - 1));
    }
}
