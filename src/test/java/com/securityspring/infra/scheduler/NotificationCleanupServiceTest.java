package com.securityspring.infra.scheduler;

import com.securityspring.core.ports.out.notification.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupServiceTest {

    @Mock
    NotificationRepository notificationRepository;

    @Test
    void cleanup_passa_cutoff_correto_para_repositorio() {
        NotificationCleanupService service = new NotificationCleanupService(notificationRepository, 90);

        Instant before = Instant.now();
        service.cleanup();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteReadBefore(captor.capture());

        Instant cutoff = captor.getValue();
        assertThat(cutoff).isBefore(before.minusSeconds(90L * 24 * 3600 - 1));
        assertThat(cutoff).isAfter(after.minusSeconds(90L * 24 * 3600 + 5));
    }

    @Test
    void cleanup_usa_retention_days_configuravel() {
        NotificationCleanupService service = new NotificationCleanupService(notificationRepository, 30);

        service.cleanup();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteReadBefore(captor.capture());

        Instant cutoff = captor.getValue();
        Instant expected30Days = Instant.now().minusSeconds(30L * 24 * 3600);
        assertThat(cutoff).isCloseTo(expected30Days, org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void cleanup_delega_para_repositorio() {
        NotificationCleanupService service = new NotificationCleanupService(notificationRepository, 90);

        service.cleanup();

        verify(notificationRepository).deleteReadBefore(org.mockito.ArgumentMatchers.any(Instant.class));
    }
}
