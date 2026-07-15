package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.notification.PasswordResetTokenRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PasswordResetTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanupService.class);

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PasswordResetTokenCleanupService(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    @Scheduled(cron = "${password-reset.cleanup.cron:0 15 3 * * *}")
    @SchedulerLock(name = "passwordResetTokenCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        log.info("scheduler.password-reset.cleanup.start");
        passwordResetTokenRepository.deleteExpiredBefore(Instant.now());
        log.info("scheduler.password-reset.cleanup.done");
    }
}
