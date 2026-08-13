package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase.KitComponentCommand;
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
 * Prova que a proteção otimista de {@code stock_balance} (EST-C007 — mesma exercida
 * isoladamente por {@link StockBalanceConcurrencyIT} e via PDV puro por
 * {@link PdvSaleConcurrencyIT}) também vale quando duas VIAS DE VENDA DIFERENTES disputam o
 * MESMO saldo de componente ao mesmo tempo: vender o kit — que {@code
 * EstoqueService#explodeKitMovement} explode numa baixa do componente — e vender o componente
 * avulso diretamente. Nenhuma das duas vias pode "ganhar de graça" às custas da outra: o saldo
 * final do componente compartilhado tem que refletir exatamente o total de vendas confirmadas,
 * kit + avulso somadas.
 *
 * <p>Fica num arquivo separado de {@link KitSaleFlowIT} porque o padrão de concorrência
 * ({@code CountDownLatch}/{@code ExecutorService}, sem {@code @Transactional} de classe — cada
 * thread precisa da própria transação para o {@code @Version} de {@code stock_balance} entrar em
 * jogo) é o mesmo de {@link StockBalanceConcurrencyIT}/{@link PdvSaleConcurrencyIT}, e destoaria
 * no meio dos testes sequenciais de {@code KitSaleFlowIT}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class KitSaleConcurrencyIT {

    private static final int THREADS = 8;
    private static final int INITIAL_STOCK = 5;

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    /** Dispara uma ação por thread simultaneamente e devolve [sucessos, conflitos]. */
    private int[] runConcurrently(List<Runnable> actions) throws Exception {
        int threads = actions.size();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        for (Runnable action : actions) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                    successes.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException
                        | InsufficientStockException e) {
                    // Conflito esperado: perdeu a corrida do @Version em stock_balance, ou chegou
                    // depois que o saldo compartilhado já tinha zerado (kit ou avulso, tanto faz —
                    // as duas vias passam pelo mesmo adjustStock/StockBalance.apply). Vira 409/422
                    // na API.
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
    void kitSale_and_directComponentSale_neverOversellSharedComponentBalance() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String warehouseCode = "CONC_KIT_WH_" + suffix;
        String operator = "caixa-" + suffix;
        String componentSku = "CONC_KIT_COMP_" + suffix;
        String kitSku = "CONC_KIT_SKU_" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja de concorrência", WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(componentSku, "Componente de concorrência", "testes", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")),
                null, null, false, false, null, null, List.of(), List.of(), null,
                null, null, false, true, null, null);
        estoqueUseCase.adjustStock(componentSku, warehouseCode, MovementType.ENTRADA,
                new BigDecimal(INITIAL_STOCK), "carga inicial", operator);

        estoqueUseCase.createProduct(kitSku, "Kit de concorrência", "combo", List.of(),
                Pricing.of(null, null, new BigDecimal("20.00")));
        estoqueUseCase.defineKitRecipe(kitSku, List.of(new KitComponentCommand(componentSku, BigDecimal.ONE)));

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);

        // 4 threads vendem o KIT (explode em 1 baixa do componente via explodeKitMovement), 4
        // vendem o COMPONENTE AVULSO diretamente — as duas vias competem pelo MESMO saldo de
        // stock_balance, com só INITIAL_STOCK unidades pra THREADS pretendentes.
        List<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < THREADS / 2; i++) {
            actions.add(() -> pdvUseCase.registerSale(session.id(), null,
                    List.of(new SaleItemCommand(kitSku, BigDecimal.ONE, null)),
                    List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null)), operator));
            actions.add(() -> pdvUseCase.registerSale(session.id(), null,
                    List.of(new SaleItemCommand(componentSku, BigDecimal.ONE, null)),
                    List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null)), operator));
        }

        int[] result = runConcurrently(actions);
        int successes = result[0];
        int conflicts = result[1];

        assertThat(successes + conflicts)
                .as("toda thread ou vende (kit ou avulso) ou perde a corrida — nenhuma pode falhar de outro jeito")
                .isEqualTo(THREADS);
        assertThat(successes).as("ao menos uma venda precisa passar").isPositive();
        assertThat(successes)
                .as("%d threads (kit + avulso) competindo por %d unidades do componente compartilhado: "
                        + "nenhuma combinação pode vender além do saldo disponível", THREADS, INITIAL_STOCK)
                .isLessThanOrEqualTo(INITIAL_STOCK);

        StockBalance finalBalance = estoqueUseCase.getStockBalance(componentSku, warehouseCode);
        assertThat(finalBalance.quantity())
                .as("o saldo final do componente tem que refletir exatamente as %d vendas confirmadas "
                        + "(kit + avulso somadas) — qualquer valor diferente é lost update ou oversell", successes)
                .isEqualByComparingTo(new BigDecimal(INITIAL_STOCK - successes));
    }
}
