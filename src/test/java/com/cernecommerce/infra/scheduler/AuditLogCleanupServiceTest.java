package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogCleanupServiceTest {

    @Mock AuditLogRepository auditLogRepository;

    @Test
    void cleanup_deletes_entries_older_than_retention_period() {
        AuditLogCleanupService service = new AuditLogCleanupService(auditLogRepository, 90);
        when(auditLogRepository.deleteOlderThan(org.mockito.ArgumentMatchers.any())).thenReturn(5L);

        Instant before = Instant.now();
        service.cleanup();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogRepository).deleteOlderThan(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isBefore(before.minusSeconds(90 * 24 * 3600 - 1));
        assertThat(cutoff).isAfter(after.minusSeconds(90 * 24 * 3600 + 5));
    }
}
