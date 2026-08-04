package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
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
 * Dois reembolsos concorrentes do mesmo pedido não duplicam o estorno (PDV-F007, e o
 * {@code @Version} de {@code OrderEntity}).
 *
 * <p>{@code OrderService.refundOrder} valida a transição de status (via {@code Order.refunded},
 * que chama {@code requireTransition}) ANTES de tocar estoque, pagamento ou cashback — em tese a
 * máquina de estados já bastaria sozinha. Mas a leitura do pedido ({@code getOrder}) acontece no
 * início de CADA transação: se duas chamadas concorrentes lerem o mesmo pedido {@code CONCLUIDO}
 * antes de qualquer uma commitar, as duas passam pela checagem de domínio em memória e só disputam
 * de verdade no {@code @Version}, no {@code orderRepository.save(refunded)} final.</p>
 *
 * <p>Como cada chamada roda na sua própria transação (nenhuma reentra na de outra), a que perde
 * essa disputa tem TODO o seu trabalho — reposição de estoque, pagamento estornado, cashback
 * revertido — desfeito pelo rollback quando o save final lança
 * {@link ObjectOptimisticLockingFailureException}, não só o UPDATE do pedido em si. E uma thread
 * que só chegue a ler o pedido depois que outra já commitou o reembolso encontra
 * {@code REEMBOLSADO} e nem chega a escrever nada: {@code requireTransition} barra com
 * {@link InvalidOrderStatusTransitionException} antes de qualquer efeito colateral. As duas rotas
 * levam ao mesmo lugar — nunca mais de 1 reembolso passa, não importa o timing real de cada
 * thread — por isso {@code successes == 1} é uma garantia da máquina de estados + versão, não uma
 * probabilidade.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderRefundConcurrencyIT {

    private static final int THREADS = 8;

    @Autowired OrderUseCase orderUseCase;
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
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException
                        | InvalidOrderStatusTransitionException e) {
                    // Conflito esperado: perdeu o @Version na escrita final do pedido, ou leu o
                    // pedido já reembolsado por outra thread e a máquina de estados barrou antes
                    // de qualquer escrita. Os dois viram 409 na API.
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
    void concurrentRefunds_onlyOneSucceedsAndEffectsAreNotDuplicated() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String operator = "caixa-" + suffix;
        String warehouseCode = "CONC_RF_WH_" + suffix;
        String sku = "CONC_RF_SKU_" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja de concorrência", WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Produto de concorrência refund", "testes", List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);

        Customer customer = crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                uniqueCpf(suffix), "balcao");

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(session.id(), customer.id(),
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("100.00"), null)), operator);

        // Pré-condição: 1 venda concluída, estoque baixado, 1 EARNED em carência.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity()).isEqualByComparingTo("9.000");

        int[] result = runConcurrently(
                () -> orderUseCase.refundOrder(order.id(), "motivo concorrente", operator));
        int successes = result[0];
        int conflicts = result[1];

        assertThat(successes + conflicts)
                .as("toda thread ou reembolsa ou perde a corrida — nenhuma pode falhar de outro jeito")
                .isEqualTo(THREADS);
        assertThat(successes)
                .as("um pedido só pode ser reembolsado uma vez, não importa quantas threads disputem")
                .isEqualTo(1);

        Order finalOrder = orderUseCase.getOrder(order.id());
        assertThat(finalOrder.status()).isEqualTo(OrderStatus.REEMBOLSADO);

        // Estoque: reposto exatamente uma vez — 9 (pós-venda) + 1 (um único reembolso) = 10, nunca
        // mais, mesmo com 8 threads tentando repor em paralelo.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .as("nenhuma reposição duplicada: o estoque tem que voltar exatamente ao que era antes da venda")
                .isEqualByComparingTo("10.000");

        // Pagamento: a linha CAPTURED original permanece, mais exatamente 1 REFUNDED — não 1 por
        // thread que tentou reembolsar.
        List<OrderPayment> payments = pdvUseCase.getOrderPayments(order.id());
        assertThat(payments).hasSize(2);
        assertThat(payments.stream().filter(p -> p.status() == PaymentStatus.REFUNDED).count())
                .as("um único estorno de pagamento")
                .isEqualTo(1L);

        // Cashback: o EARNED original continua no extrato, mais exatamente 1 REVERSED.
        List<CashbackEntry> entries = cashbackUseCase.listCustomerEntries(customer.id(), 0, 20).content();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().filter(e -> e.type() == CashbackEntryType.REVERSED).count())
                .as("uma única reversão de cashback")
                .isEqualTo(1L);

        CashbackBalance balance = cashbackUseCase.getCustomerBalance(customer.id());
        assertThat(balance.pending())
                .as("o ganho revertido não pode contar mais como pendente")
                .isEqualByComparingTo("0.00");
    }
}
