package com.cernecommerce.infra.scheduler;

import com.cernecommerce.core.ports.in.EstoqueUseCase;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredor de reserva de estoque vencida (EST-F013/EST-F021).
 *
 * <p>Devolve ao disponível toda {@code StockReservation} {@code ACTIVE} cujo {@code expiresAt} já
 * passou — o caso do carrinho abandonado no checkout. Roda bem mais frequente que os outros
 * {@code *CleanupService} deste pacote de propósito: o TTL padrão da reserva é 30 minutos
 * ({@code estoque.reservation.default-ttl}), e uma varredura diária deixaria saldo travado por até
 * quase um dia depois de vencido — exatamente o "estoque travado invisível" que a reserva existe
 * para não causar.</p>
 */
@Component
public class StockReservationExpiryCleanupService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationExpiryCleanupService.class);

    private final EstoqueUseCase estoqueUseCase;
    private final int batchSize;

    public StockReservationExpiryCleanupService(EstoqueUseCase estoqueUseCase,
            @Value("${estoque.reservation.expiry.batch-size:200}") int batchSize) {
        this.estoqueUseCase = estoqueUseCase;
        this.batchSize = batchSize;
    }

    // lockAtMostFor cobre uma varredura anormalmente lenta sem travar a próxima passada para
    // sempre; lockAtLeastFor evita reexecução imediata em caso de falha rápida logo no início.
    @Scheduled(cron = "${estoque.reservation.expiry.cron:0 */5 * * * *}")
    @SchedulerLock(name = "stockReservationExpiryCleanup", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void cleanup() {
        log.info("scheduler.stock-reservation.expiry.start batchSize={}", batchSize);
        int expired = estoqueUseCase.expireReservations(batchSize);
        log.info("scheduler.stock-reservation.expiry.done expired={}", expired);
    }
}
