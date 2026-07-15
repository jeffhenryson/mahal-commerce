package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.notification.EmailVerificationCodeRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EmailVerificationCodeCleanupService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationCodeCleanupService.class);

    private final EmailVerificationCodeRepository repository;

    public EmailVerificationCodeCleanupService(EmailVerificationCodeRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "${email-verification.cleanup.cron:0 30 3 * * *}")
    @SchedulerLock(name = "emailVerificationCodeCleanup", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        log.info("scheduler.email-verification.cleanup.start");
        repository.deleteExpiredBefore(Instant.now());
        log.info("scheduler.email-verification.cleanup.done");
    }
}
