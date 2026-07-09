package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.notification.NotificationRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class NotificationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

    private final NotificationRepository notificationRepository;
    private final long retentionDays;

    public NotificationCleanupService(NotificationRepository notificationRepository,
            @Value("${notification.read.retention-days:90}") long retentionDays) {
        this.notificationRepository = notificationRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${notification.cleanup.cron:0 0 4 * * *}")
    @SchedulerLock(name = "notificationCleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("scheduler.notification.cleanup.start retentionDays={} cutoff={}", retentionDays, cutoff);
        notificationRepository.deleteReadBefore(cutoff);
        log.info("scheduler.notification.cleanup.done");
    }
}
