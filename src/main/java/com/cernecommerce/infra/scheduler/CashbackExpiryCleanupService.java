package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.CashbackUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredor de ganhos de cashback vencidos (CRM-F003).
 *
 * <p>Grava a entrada {@code EXPIRED} de todo ganho {@code EARNED} cujo {@code expiresAt} já
 * passou. Ao contrário do varredor de reserva de estoque (TTL de minutos), a expiração de
 * cashback é de meses (default 180 dias) — uma passada diária é suficiente, sem o risco de
 * "estoque travado invisível" que justifica a cadência de 5 minutos ali.</p>
 */
@Component
public class CashbackExpiryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(CashbackExpiryCleanupService.class);

    private final CashbackUseCase cashbackUseCase;
    private final int batchSize;

    public CashbackExpiryCleanupService(CashbackUseCase cashbackUseCase,
            @Value("${cashback.expiry.batch-size:200}") int batchSize) {
        this.cashbackUseCase = cashbackUseCase;
        this.batchSize = batchSize;
    }

    // lockAtMostFor cobre uma varredura anormalmente lenta sem travar a próxima passada para
    // sempre; lockAtLeastFor evita reexecução imediata em caso de falha rápida logo no início.
    @Scheduled(cron = "${cashback.expiry.cron:0 30 3 * * *}")
    @SchedulerLock(name = "cashbackExpiryCleanup", lockAtMostFor = "PT55M", lockAtLeastFor = "PT5M")
    public void cleanup() {
        log.info("scheduler.cashback.expiry.start batchSize={}", batchSize);
        int expired = cashbackUseCase.expireEntries(batchSize);
        log.info("scheduler.cashback.expiry.done expired={}", expired);
    }
}
