package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
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
 * Prova que o {@code @Version} de {@code stock_balance} realmente protege o saldo sob escrita
 * concorrente (EST-C007).
 *
 * <p>Este é o único optimistic locking do projeto e, até esta sprint, não tinha nenhum teste — o
 * mecanismo que impede duas vendas simultâneas de corromperem o saldo era confiança, não
 * evidência. Sem o {@code @Version}, duas threads leriam o mesmo saldo, ambas escreveriam
 * {@code saldo - 1} e uma das baixas sumiria (lost update), deixando o estoque do sistema acima do
 * estoque real.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockBalanceConcurrencyIT {

    private static final int THREADS = 8;

    @Autowired
    private EstoqueUseCase estoqueUseCase;

    private String setUpCatalog(String prefix, String warehouseCode) {
        String sku = prefix + "_" + System.nanoTime();
        estoqueUseCase.createProduct(sku, "Produto de concorrência", "testes", List.of());
        estoqueUseCase.createWarehouse(warehouseCode, "Depósito de concorrência", WarehouseType.LOJA_FISICA);
        return sku;
    }

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
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
                    // Conflito esperado: o perdedor da corrida. Vira 409 na API.
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
    void saidas_concorrentes_nao_perdem_baixa_de_estoque() throws Exception {
        String warehouseCode = "CONC_WH_" + System.nanoTime();
        String sku = setUpCatalog("CONC_SKU", warehouseCode);
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("100.000"),
                "Carga inicial", "gerente");

        int[] result = runConcurrently(() -> estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.SAIDA,
                new BigDecimal("1.000"), "Venda concorrente", "caixa"));
        int successes = result[0];
        int conflicts = result[1];

        assertThat(successes + conflicts)
                .as("toda thread ou dá baixa ou perde a corrida — nenhuma pode falhar de outro jeito")
                .isEqualTo(THREADS);
        assertThat(successes).as("ao menos uma baixa precisa passar").isPositive();

        StockBalance finalBalance = estoqueUseCase.getStockBalance(sku, warehouseCode);
        assertThat(finalBalance.quantity())
                .as("o saldo final tem que refletir exatamente as %d baixas confirmadas — "
                        + "qualquer valor maior é lost update", successes)
                .isEqualByComparingTo(new BigDecimal("100.000").subtract(new BigDecimal(successes)));
    }

    @Test
    void primeira_movimentacao_concorrente_do_mesmo_par_nao_duplica_saldo() throws Exception {
        // EST-C010: sem linha anterior não há version para conferir, então a proteção aqui é a
        // unique constraint uk_stock_balance_sku_warehouse. O perdedor tem que virar conflito
        // tratado (409), não 500.
        String warehouseCode = "CONC_NEW_WH_" + System.nanoTime();
        String sku = setUpCatalog("CONC_NEW_SKU", warehouseCode);

        int[] result = runConcurrently(() -> estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA,
                new BigDecimal("1.000"), "Entrada concorrente", "gerente"));
        int successes = result[0];
        int conflicts = result[1];

        assertThat(successes + conflicts).isEqualTo(THREADS);
        assertThat(successes).isPositive();

        StockBalance finalBalance = estoqueUseCase.getStockBalance(sku, warehouseCode);
        assertThat(finalBalance.quantity())
                .as("o saldo tem que corresponder às %d entradas confirmadas", successes)
                .isEqualByComparingTo(new BigDecimal(successes));
    }
}
