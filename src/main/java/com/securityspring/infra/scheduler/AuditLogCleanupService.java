package com.securityspring.infra.scheduler;

import com.securityspring.core.ports.out.audit.AuditLogRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AuditLogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogCleanupService.class);

    private final AuditLogRepository auditLogRepository;
    private final long retentionDays;

    public AuditLogCleanupService(AuditLogRepository auditLogRepository,
            @Value("${audit.retention-days:365}") long retentionDays) {
        this.auditLogRepository = auditLogRepository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${audit.cleanup.cron:0 45 3 * * *}")
    @SchedulerLock(name = "auditLogCleanup", lockAtMostFor = "PT55M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        log.info("scheduler.audit.cleanup.start retentionDays={} cutoff={}", retentionDays, cutoff);
        long deleted = auditLogRepository.deleteOlderThan(cutoff);
        log.info("scheduler.audit.cleanup.done deleted={}", deleted);
    }
}
