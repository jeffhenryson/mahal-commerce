package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.EstoqueUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredor de vencimento próximo de lote (EST-F008).
 *
 * <p>Notifica quem tem {@code ESTOQUE_STOCK_MANAGE} sobre todo {@code StockLot} que vence dentro
 * de {@code cutoffDays} e ainda não foi alertado. Roda uma vez por dia — diferente do varredor de
 * reserva (a cada 5 min): validade se mede em dias/semanas, não minutos, e não há "estoque
 * travado invisível" esperando o próximo run como há na reserva vencida.</p>
 */
@Component
public class StockLotExpiryAlertService {

    private static final Logger log = LoggerFactory.getLogger(StockLotExpiryAlertService.class);

    private final EstoqueUseCase estoqueUseCase;
    private final int cutoffDays;
    private final int batchSize;

    public StockLotExpiryAlertService(EstoqueUseCase estoqueUseCase,
            @Value("${estoque.lot.expiry-alert.cutoff-days:7}") int cutoffDays,
            @Value("${estoque.lot.expiry-alert.batch-size:200}") int batchSize) {
        this.estoqueUseCase = estoqueUseCase;
        this.cutoffDays = cutoffDays;
        this.batchSize = batchSize;
    }

    // lockAtMostFor cobre uma varredura anormalmente lenta sem travar a próxima passada para
    // sempre; lockAtLeastFor evita reexecução imediata em caso de falha rápida logo no início —
    // mesmo par de guardas de StockReservationExpiryCleanupService, só que num período mais largo
    // porque a passada aqui é diária, não a cada 5 min.
    @Scheduled(cron = "${estoque.lot.expiry-alert.cron:0 0 7 * * *}")
    @SchedulerLock(name = "stockLotExpiryAlert", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1M")
    public void alert() {
        log.info("scheduler.stock-lot.expiry-alert.start cutoffDays={} batchSize={}", cutoffDays, batchSize);
        int alerted = estoqueUseCase.alertExpiringLots(cutoffDays, batchSize);
        log.info("scheduler.stock-lot.expiry-alert.done alerted={}", alerted);
    }
}
