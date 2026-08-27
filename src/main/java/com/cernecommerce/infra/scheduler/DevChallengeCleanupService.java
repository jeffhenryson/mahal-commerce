package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.twofa.DevChallengeRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DevChallengeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DevChallengeCleanupService.class);

    private final DevChallengeRepository devChallengeRepository;

    public DevChallengeCleanupService(DevChallengeRepository devChallengeRepository) {
        this.devChallengeRepository = devChallengeRepository;
    }

    @Scheduled(cron = "${dev.challenge.cleanup.cron:0 45 3 * * *}")
    @SchedulerLock(name = "devChallengeCleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void cleanup() {
        log.info("scheduler.dev.challenge.cleanup.start");
        devChallengeRepository.deleteExpiredBefore(Instant.now());
        log.info("scheduler.dev.challenge.cleanup.done");
    }
}
