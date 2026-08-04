package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que uma venda de balcão concorrente não vende mais do que o estoque tem (PDV-F004 +
 * EST-C007 juntos): a mesma proteção otimista de {@code stock_balance} que
 * {@link StockBalanceConcurrencyIT} exercita isoladamente, agora atravessada por
 * {@code pdvUseCase.registerSale} de ponta a ponta — sessão, pedido, pagamento e cashback
 * inclusos na mesma transação que a baixa de estoque.
 *
 * <p>A concorrência aqui é sobre o SALDO DE ESTOQUE, não sobre a posse da sessão: as
 * {@value #THREADS} threads compartilham de propósito a mesma sessão de caixa e o mesmo
 * operador — {@code requireOwnOpenSession} não é o que está sob teste.</p>
 *
 * <p><b>Duas causas possíveis de "conflito esperado" aqui</b>, ambas capturadas: (1)
 * {@link InsufficientStockException} — {@code StockBalance.apply} recusa uma SAÍDA maior que o
 * saldo lido no início da transação; (2) {@link ObjectOptimisticLockingFailureException} (ou
 * {@link DataIntegrityViolationException}) — o {@code @Version} de {@code stock_balance} recusa
 * o UPDATE se outra transação já commitou por cima do saldo que esta leu. Qual das duas aparece
 * para cada thread perdedora depende de timing real (quando cada uma leu o saldo, não de retry):
 * {@code PdvService}/{@code EstoqueService} não têm nenhuma lógica de nova tentativa automática
 * em conflito otimista (confirmado: nenhum {@code @Retryable}/retry manual no projeto).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdvSaleConcurrencyIT {

    private static final int THREADS = 8;
    private static final int INITIAL_STOCK = 5;

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    /** Dispara {@code THREADS} chamadas simultâneas e devolve [sucessos, conflitos]. */
    private int[] runConcurrently(Runnable action) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                    successes.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException
                        | InsufficientStockException e) {
                    // Conflito esperado: ou perdeu a corrida do @Version em stock_balance, ou
                    // chegou depois que o saldo já tinha zerado. Os dois viram 409/422 na API.
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();
        return new int[] { successes.get(), conflicts.get() };
    }

    @Test
    void concurrentSales_neverOversellBeyondAvailableStock() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String warehouseCode = "CONC_PDV_WH_" + suffix;
        String sku = "CONC_PDV_SKU_" + suffix;
        String operator = "caixa-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja de concorrência", WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Produto de concorrência PDV", "testes", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA,
                new BigDecimal(INITIAL_STOCK), "carga inicial", operator);

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);

        // 8 threads, mesma sessão/operador, cada uma vendendo 1 unidade de um SKU com só 5 em
        // estoque — mais compradores concorrentes do que mercadoria disponível.
        int[] result = runConcurrently(() -> pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null)), operator));
        int successes = result[0];
        int conflicts = result[1];

        assertThat(successes + conflicts)
                .as("toda thread ou vende ou perde a corrida — nenhuma pode falhar de outro jeito")
                .isEqualTo(THREADS);
        assertThat(successes)
                .as("ao menos uma venda precisa passar")
                .isPositive();
        // Sem retry automático em conflito otimista (confirmado: nenhum @Retryable no projeto),
        // threads liberadas ao mesmo tempo por runConcurrently tendem a ler o mesmo snapshot do
        // saldo e colidir — "successes" não é um número fixo previsível, pode ser 1 ou pode ser
        // até INITIAL_STOCK, dependendo de como o scheduler intercala as threads. A garantia real
        // (e a única que StockBalanceConcurrencyIT também afirma) é que NUNCA vende mais do que
        // existe: successes <= INITIAL_STOCK e o saldo final bate exatamente com o que foi vendido.
        assertThat(successes)
                .as("8 threads competindo por %d unidades: nenhuma venda pode passar além do saldo disponível",
                        INITIAL_STOCK)
                .isLessThanOrEqualTo(INITIAL_STOCK);

        StockBalance finalBalance = estoqueUseCase.getStockBalance(sku, warehouseCode);
        assertThat(finalBalance.quantity())
                .as("o saldo final tem que refletir exatamente as %d vendas confirmadas — "
                        + "qualquer valor diferente é lost update ou oversell", successes)
                .isEqualByComparingTo(new BigDecimal(INITIAL_STOCK - successes));
    }
}
