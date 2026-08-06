package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
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
 * Prova o fix da janela de negócio entre registrar uma contagem e fechar o balanço de inventário
 * (EST-F006/EST-C0xx).
 *
 * <p>Antes do fix, {@code closeStockCount} confrontava o valor contado contra o saldo lido NO
 * MOMENTO DO FECHAMENTO, e o {@code AJUSTE} gerado SUBSTITUÍA o saldo pelo valor contado direto
 * ({@code StockBalance#apply(MovementType.AJUSTE, ...)} não soma/subtrai, substitui). Uma venda
 * concorrente acontecendo entre o registro da contagem e o fechamento do balanço era apagada do
 * saldo, mesmo com o {@code StockMovement} da venda continuando gravado no ledger.</p>
 *
 * <p>O fix (ver {@code EstoqueService#recordCountedItem}/{@code StockCount#withReconciledItem})
 * carimba {@code expectedQuantity}/{@code difference} no REGISTRO da contagem, não no fechamento.
 * No fechamento, {@code closeAggregateSku}/{@code closeLotTrackedSku} somam essa divergência
 * (já conhecida desde o registro) ao saldo ATUAL, em vez de substituir o saldo pelo contado —
 * então uma movimentação legítima entre o registro e o fechamento continua refletida no saldo
 * final, e só a divergência que a contagem de fato encontrou é corrigida.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StockCountConcurrencyIT {

    private static final int THREADS = 8;

    @Autowired
    private EstoqueUseCase estoqueUseCase;

    private String setUpCatalog(String prefix, String warehouseCode) {
        String sku = prefix + "_" + System.nanoTime();
        estoqueUseCase.createProduct(sku, "Produto de concorrência de balanço", "testes", List.of());
        estoqueUseCase.createWarehouse(warehouseCode, "Depósito de concorrência de balanço",
                WarehouseType.LOJA_FISICA);
        return sku;
    }

    /**
     * Dispara {@code THREADS} chamadas simultâneas e devolve [sucessos, conflitos]. Mesmo padrão
     * de {@code StockBalanceConcurrencyIT}.
     */
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
    void venda_entre_registro_e_fechamento_do_balanco_nao_e_apagada_pelo_ajuste() {
        String warehouseCode = "SC_WH_" + System.nanoTime();
        String sku = setUpCatalog("SC_SKU", warehouseCode);
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "Carga inicial", "gerente");

        StockCount count = estoqueUseCase.openStockCount(warehouseCode, "contador");

        // Contador registra o que viu na prateleira: 8, faltam 2 em relação ao saldo do sistema
        // (10) NESTE instante. O snapshot expectedQuantity=10/difference=-2 é carimbado agora.
        estoqueUseCase.recordCountedItem(count.id(), sku, new BigDecimal("8.000"));

        // DEPOIS do registro, uma venda concorrente consome 3 unidades — o saldo do sistema passa
        // a ser 7. Isso não tem relação nenhuma com a divergência de 2 que o contador encontrou.
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.SAIDA, new BigDecimal("3.000"),
                "Venda concorrente depois do registro da contagem", "caixa");

        StockCount closed = estoqueUseCase.closeStockCount(count.id(), "contador");

        StockCountItem item = closed.items().stream()
                .filter(i -> i.sku().equals(sku))
                .findFirst()
                .orElseThrow();

        // expectedQuantity/difference são os do REGISTRO (10 / -2), não recalculados no
        // fechamento — a venda concorrente não teve chance de "vazar" para dentro deles.
        assertThat(item.expectedQuantity())
                .as("expectedQuantity é o saldo do REGISTRO da contagem, carimbado ali, não recalculado no fechamento")
                .isEqualByComparingTo(new BigDecimal("10.000"));
        assertThat(item.difference())
                .as("difference = counted(8) - expected(10), a divergência real encontrada na contagem")
                .isEqualByComparingTo(new BigDecimal("-2.000"));
        assertThat(item.diverges()).isTrue();

        // O AJUSTE soma a divergência (-2) ao saldo ATUAL do fechamento (7, já com a venda
        // concorrente aplicada): 7 + (-2) = 5. A venda de 3 unidades continua refletida — só a
        // divergência de 2 que o contador de fato encontrou foi corrigida.
        StockBalance finalBalance = estoqueUseCase.getStockBalance(sku, warehouseCode);
        assertThat(finalBalance.quantity())
                .as("5 = saldo atual no fechamento (7, já com a venda concorrente) + divergência (-2) — "
                        + "nem os 8 contados direto (apagaria a venda) nem os 7 ignorando a divergência encontrada")
                .isEqualByComparingTo(new BigDecimal("5.000"));
    }

    @Test
    void vendas_concorrentes_depois_do_registro_continuam_refletidas_apos_fechamento() throws Exception {
        String warehouseCode = "SC_CONC_WH_" + System.nanoTime();
        String sku = setUpCatalog("SC_CONC_SKU", warehouseCode);
        BigDecimal initial = new BigDecimal("100.000");
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, initial, "Carga inicial", "gerente");

        StockCount count = estoqueUseCase.openStockCount(warehouseCode, "contador");

        // Contador registra 95: encontrou 5 faltando em relação ao saldo do sistema (100) NESTE
        // instante. Snapshot expectedQuantity=100/difference=-5 carimbado agora, antes de
        // qualquer venda concorrente acontecer.
        BigDecimal counted = new BigDecimal("95.000");
        estoqueUseCase.recordCountedItem(count.id(), sku, counted);

        // DEPOIS do registro, N vendas concorrentes de 1 unidade cada acontecem — sem relação
        // nenhuma com a divergência de 5 que o contador já tinha encontrado e registrado.
        int[] result = runConcurrently(() -> estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.SAIDA,
                new BigDecimal("1.000"), "Venda concorrente depois do registro da contagem", "caixa"));
        int successes = result[0];
        int conflicts = result[1];
        assertThat(successes + conflicts)
                .as("toda thread ou dá baixa ou perde a corrida do @Version — nenhuma pode falhar de outro jeito")
                .isEqualTo(THREADS);
        assertThat(successes).as("ao menos uma venda concorrente precisa passar").isPositive();

        StockCount closed = estoqueUseCase.closeStockCount(count.id(), "contador");

        StockCountItem item = closed.items().stream()
                .filter(i -> i.sku().equals(sku))
                .findFirst()
                .orElseThrow();

        assertThat(item.expectedQuantity())
                .as("expected é o saldo do REGISTRO (100), carimbado antes das vendas concorrentes")
                .isEqualByComparingTo(initial);
        assertThat(item.difference())
                .as("difference = counted(95) - expected(100), a divergência real encontrada na contagem")
                .isEqualByComparingTo(new BigDecimal("-5.000"));

        // O AJUSTE final soma a divergência (-5) ao saldo ATUAL do fechamento (100 - successes,
        // já com as vendas concorrentes aplicadas) — nenhuma delas é apagada.
        BigDecimal expectedFinal = initial.subtract(new BigDecimal(successes)).subtract(new BigDecimal("5.000"));
        StockBalance finalBalance = estoqueUseCase.getStockBalance(sku, warehouseCode);
        assertThat(finalBalance.quantity())
                .as("saldo atual no fechamento (100 - %d vendas) + divergência (-5) = %s — as vendas "
                        + "concorrentes continuam refletidas, só a divergência real da contagem foi corrigida",
                        successes, expectedFinal)
                .isEqualByComparingTo(expectedFinal);
    }
}
