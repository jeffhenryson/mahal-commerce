package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.twofa.TotpConfigRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TotpPendingSetupCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TotpPendingSetupCleanupService.class);

    private final TotpConfigRepository totpConfigRepository;
    private final long pendingTtlHours;

    public TotpPendingSetupCleanupService(TotpConfigRepository totpConfigRepository,
            @Value("${totp.pending-setup.ttl-hours:24}") long pendingTtlHours) {
        this.totpConfigRepository = totpConfigRepository;
        this.pendingTtlHours = pendingTtlHours;
    }

    @Scheduled(cron = "${totp.pending-setup.cleanup.cron:0 45 3 * * *}")
    @SchedulerLock(name = "totpPendingSetupCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        log.info("scheduler.totp-pending-setup.cleanup.start");
        totpConfigRepository.deleteUnconfirmedBefore(Instant.now().minus(pendingTtlHours, ChronoUnit.HOURS));
        log.info("scheduler.totp-pending-setup.cleanup.done");
    }
}
