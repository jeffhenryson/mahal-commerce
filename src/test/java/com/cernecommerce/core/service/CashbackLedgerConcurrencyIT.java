package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
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
 * Integridade de {@code CashbackService.recordEarnedForOrder} sob chamada concorrente direta para
 * o MESMO pedido — achado real de falta de proteção, não um cenário hipotético.
 *
 * <h2>Por que não é um teste de "resgate em dobro"</h2>
 * <p>Um double-spend clássico (duas threads resgatando o mesmo saldo) não é testável hoje: não
 * existe {@code redeem} nem ajuste manual em produção. {@link CashbackUseCase} só expõe
 * {@code recordEarnedForOrder}, {@code reverseEarningsForOrder} e {@code expireEntries} como
 * mutações — a própria Javadoc da interface documenta que resgate e ajuste manual "ficam para uma
 * fatia seguinte".</p>
 *
 * <h2>O que este teste prova em vez disso</h2>
 * <p>{@code CashbackService.recordEarnedForOrder} (linha ~134) insere entradas {@code EARNED}
 * para os itens do pedido incondicionalmente — sem checar se aquele {@code orderId} já tem
 * lançamento. {@code CashbackEntryEntity} não declara {@code @Version} nem qualquer unique
 * constraint em {@code order_id}/{@code order_item_id}. Nada no schema ou na entity impede duas
 * chamadas de inserirem duas vezes o mesmo ganho.</p>
 *
 * <h2>Isto é alcançável em produção hoje?</h2>
 * <p>Não pelo caminho normal. {@code PdvService.registerSale} e {@code PaymentWebhookService}
 * chamam {@code recordEarnedForOrder} uma única vez cada, dentro da mesma transação que
 * conclui/confirma o pagamento do pedido — não há dois gatilhos concorrentes batendo nesse método
 * para o mesmo pedido no fluxo atual. Este teste dispara chamadas diretas e concorrentes ao caso
 * de uso (contornando o caminho de produção de propósito) porque é a única forma de exercitar a
 * ausência de proteção do método em si. Serve como característica documentada do método isolado, e
 * como rede de segurança para se um caminho futuro (reprocessamento de webhook, retry de fila,
 * reentrega de evento) voltar a chamá-lo mais de uma vez para o mesmo pedido.</p>
 *
 * <h2>Resultado esperado — comportamento CORRETO, não necessariamente o atual</h2>
 * <p>Depois de 1 chamada orgânica (dentro de {@code registerSale}) mais {@value #THREADS} chamadas
 * diretas concorrentes para o MESMO pedido, o total ganho deveria continuar sendo o de UMA
 * passada — {@code recordEarnedForOrder} deveria ser idempotente por pedido. Se o método
 * realmente duplicar o ganho (sem proteção nenhuma, que é o que a leitura do código atual sugere),
 * a asserção abaixo FALHA de propósito: ela não foi enfraquecida para forçar o teste a passar.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CashbackLedgerConcurrencyIT {

    private static final int THREADS = 8;

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired CashbackUseCase cashbackUseCase;

    /** cpf é VARCHAR(11) — precisa de um valor numérico derivado do sufixo, não do sufixo em si. */
    private String uniqueCpf(String suffix) {
        return String.valueOf(10000000000L + (Math.abs((long) suffix.hashCode()) % 89999999999L));
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
                    // Não há @Version nem unique constraint em cashback_entry hoje — este catch
                    // existe só para não confundir uma falha de infraestrutura inesperada com a
                    // asserção de negócio abaixo; não se espera que dispare nenhuma vez.
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
    void concurrentDirectCallsForTheSameOrder_doNotDuplicateTheEarnedCashback() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String operator = "caixa-" + suffix;
        String warehouseCode = "CONC_CB_WH_" + suffix;
        String sku = "CONC_CB_SKU_" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja de concorrência", WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Produto de concorrência cashback", "testes", List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);

        Customer customer = crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                uniqueCpf(suffix), "balcao");

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        // registerSale já lança o EARNED de uma passada (PdvService.registerSale linha ~201) — é
        // a baseline "uma chamada" com que o resultado das THREADS chamadas diretas será comparado.
        Order order = pdvUseCase.registerSale(session.id(), customer.id(),
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("100.00"), null)), operator);

        List<CashbackEntry> baseline = cashbackUseCase.listCustomerEntries(customer.id(), 0, 50).content();
        assertThat(baseline).as("pré-condição: a venda já lançou exatamente 1 EARNED orgânico").hasSize(1);
        BigDecimal onePassTotal = baseline.get(0).amount();

        int[] result = runConcurrently(() -> cashbackUseCase.recordEarnedForOrder(order));
        int successes = result[0];
        int conflicts = result[1];

        assertThat(successes + conflicts)
                .as("toda thread termina de um jeito ou de outro — nenhuma pode falhar de forma inesperada")
                .isEqualTo(THREADS);

        List<CashbackEntry> earnedForOrder = cashbackUseCase.listCustomerEntries(customer.id(), 0, 50).content()
                .stream()
                .filter(e -> e.type() == CashbackEntryType.EARNED)
                .filter(e -> e.orderId().equals(order.id()))
                .toList();
        assertThat(earnedForOrder)
                .as("recordEarnedForOrder tem que ser idempotente por pedido: %d chamadas concorrentes "
                        + "extras para o MESMO pedido não podem gerar nenhum EARNED além do orgânico", THREADS)
                .hasSize(1);

        CashbackBalance balance = cashbackUseCase.getCustomerBalance(customer.id());
        assertThat(balance.pending())
                .as("o total pendente tem que ser o de UMA passada, não %dx maior", THREADS + 1)
                .isEqualByComparingTo(onePassTotal);
    }
}
