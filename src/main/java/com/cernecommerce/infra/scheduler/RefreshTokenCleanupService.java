package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.out.token.RefreshTokenPort;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
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

    // lockAtMostFor deve ser maior que o tempo máximo de execução e menor que o intervalo do cron.
    // lockAtLeastFor evita re-execução imediata em caso de falha rápida.
    @Scheduled(cron = "${refresh-token.cleanup.cron:0 0 3 * * *}")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "PT55M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        log.info("scheduler.refresh.cleanup.start");
        refreshTokenPort.deleteExpiredAndRevoked();
        log.info("scheduler.refresh.cleanup.done");
    }
}
