package com.securityspring.infra.scheduler;

import com.securityspring.core.ports.out.RefreshTokenPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupService.class);

    private final RefreshTokenPort refreshTokenPort;

    public RefreshTokenCleanupService(RefreshTokenPort refreshTokenPort) {
        this.refreshTokenPort = refreshTokenPort;
    }

    @Scheduled(cron = "${refresh-token.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        log.info("scheduler.refresh.cleanup.start");
        refreshTokenPort.deleteExpiredAndRevoked();
        log.info("scheduler.refresh.cleanup.done");
    }
}
