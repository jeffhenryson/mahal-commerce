package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.twofa.TotpChallengeTokenRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class TotpChallengeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TotpChallengeCleanupService.class);

    private final TotpChallengeTokenRepository totpChallengeTokenRepository;

    public TotpChallengeCleanupService(TotpChallengeTokenRepository totpChallengeTokenRepository) {
        this.totpChallengeTokenRepository = totpChallengeTokenRepository;
    }

    @Scheduled(cron = "${totp.challenge.cleanup.cron:0 30 3 * * *}")
    @SchedulerLock(name = "totpChallengeCleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT2M")
    public void cleanup() {
        log.info("scheduler.totp.challenge.cleanup.start");
        totpChallengeTokenRepository.deleteExpiredBefore(Instant.now());
        log.info("scheduler.totp.challenge.cleanup.done");
    }
}
