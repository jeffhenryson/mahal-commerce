package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-C008 — a mesa compartilhada sob concorrência real.
 *
 * <p><b>O que estava errado.</b> PDV-F010 abriu a mesa para qualquer atendente com
 * {@code PDV_COMANDA_MANAGE} ("o consumo é do salão, não do operador"), e a partir dali dois
 * lançamentos simultâneos na mesma comanda passaram a ser o caso normal de um sábado cheio.
 * {@code ComandaService} lia a comanda, decidia sobre o estado lido — {@code requireOpen} — e só
 * então gravava. Sem trava, A e B leem a mesma mesa ABERTA; B fecha e gera o pedido; a decisão que
 * A já tinha tomado continua valendo, e o item de A entra numa comanda cujo pedido <b>já foi pago
 * e fechado</b>. O estoque saiu em {@code addItem}, num commit próprio, e ninguém é cobrado por
 * ele.</p>
 *
 * <p><b>Por que a trava é pessimista.</b> Um {@code @Version} não pega este caso: a versão
 * otimista só colide quando o UPDATE chega a ser emitido, e o Hibernate compara o agregado com o
 * snapshot que ele mesmo carregou — regravar um estado velho por cima não conta como alteração,
 * nenhum UPDATE sai e nenhuma colisão é detectada. A corrida aqui é sobre a <b>leitura</b> que
 * fundamenta a decisão, e é lá que a trava tem que estar. Precedente no repositório:
 * {@code RefreshTokenJpaRepository.findByTokenHashForUpdate}.</p>
 *
 * <p><b>Sem {@code @Transactional} na classe</b>, ao contrário de {@link ComandaCashCycleIT}: cada
 * thread precisa da própria transação para a trava significar alguma coisa. Uma transação de teste
 * envolvendo tudo faria as threads compartilharem o mesmo contexto e o teste passaria por engano.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ComandaConcurrencyIT {

    private static final int THREADS = 6;

    @Autowired PdvUseCase pdvUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Os três desfechos que uma thread pode ter aqui. Vale a pena serem campos com nome: dois
     * deles são <b>corretos</b> e significam coisas diferentes, e um teste que somasse tudo em
     * "falhou" não distinguiria a recusa deliberada da colisão de saldo.
     *
     * @param successes a ação passou inteira
     * @param refusals {@code COMANDA_NOT_OPEN} — a mesa já não estava aberta na vez desta thread.
     *        É o resultado que a trava de PDV-C008 produz, e é 409 na API, não silêncio
     * @param conflicts {@code STOCK_UPDATE_CONFLICT} — perdeu a corrida pelo <b>saldo de estoque</b>,
     *        que é outro agregado, com trava <b>otimista</b>. Nada a ver com a comanda
     */
    private record Outcome(int successes, int refusals, int conflicts) {
        int total() {
            return successes + refusals + conflicts;
        }
    }

    /** Dispara {@code THREADS} ações simultâneas, todas idênticas. */
    private Outcome runConcurrently(Runnable action) throws Exception {
        return runConcurrently(i -> action.run());
    }

    /**
     * Dispara {@code THREADS} ações simultâneas, cada uma recebendo o próprio índice — é o que
     * permite dar um SKU diferente a cada thread e isolar a comanda da contenção de estoque.
     */
    private Outcome runConcurrently(IntConsumer action) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger refusals = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.accept(index);
                    successes.incrementAndGet();
                } catch (ComandaNotOpenException e) {
                    refusals.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    // Colisão no stock_balance, não na comanda — ver o javadoc de Outcome.
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
        return new Outcome(successes.get(), refusals.get(), conflicts.get());
    }

    /**
     * Lança item pelo caminho <b>que o controller usa</b> — a sobrecarga completa, que é a que
     * carrega {@code @Transactional} em {@code ComandaService}.
     *
     * <p><b>Não troque pelo atalho de quatro argumentos.</b> Ele é um {@code default} de
     * {@code ComandaUseCase} que delega para esta: chamado através do proxy JDK, o default executa
     * no <i>target</i>, então a chamada interna é self-invocation e o {@code @Transactional} do
     * método real <b>não é aplicado</b>. Cada repositório abre a própria transação, o
     * {@code SELECT FOR UPDATE} commita e solta a trava na hora, e o teste passa a medir um
     * cenário que produção não tem. Foi exatamente assim que este arquivo "provou" um lost update
     * que o código não tinha. Rastreado como <b>PLAT-C047</b>.</p>
     */
    private void addItem(Long comandaId, String sku, String operator) {
        comandaUseCase.addItem(comandaId, sku, BigDecimal.ONE, ConsumptionMode.NORMAL,
                false, null, null, null, operator);
    }

    private String givenWarehouse(String suffix) {
        String warehouseCode = "LOUNGE-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        return warehouseCode;
    }

    /** Uma essência de R$ 25 com 100 em estoque no depósito dado. */
    private String givenStockedSku(String warehouseCode, String suffix, String operator) {
        String sku = "ESS-" + suffix;
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("100.000"),
                "carga inicial", operator);
        return sku;
    }

    private String[] givenStockedWarehouse(String operator) {
        String suffix = uniqueSuffix();
        String warehouseCode = givenWarehouse(suffix);
        return new String[] { warehouseCode, givenStockedSku(warehouseCode, suffix, operator) };
    }

    /**
     * Seis atendentes lançando na mesma mesa ao mesmo tempo: <b>todos</b> têm que passar, e a
     * comanda tem que terminar com as seis linhas. Um agregado reescrito inteiro a cada
     * {@code save} é exatamente o desenho em que uma linha some — este é o teste que provaria.
     *
     * <p><b>Um SKU por thread, de propósito.</b> É o lançamento na <i>comanda</i> que este teste
     * afere, e o saldo de estoque é outro agregado: ele tem trava <b>otimista</b>
     * ({@code StockBalanceEntity.@Version}) e o projeto não tem retry, então seis threads no mesmo
     * SKU colidem lá por desenho — ruído que não diz nada sobre lost update de comanda. Separando
     * os SKUs, a única contenção que sobra é a que interessa, e a asserção vira determinística:
     * seis linhas, sempre. A convivência com aquela colisão é o teste seguinte.</p>
     */
    @Test
    void concurrentAddItem_neverLosesALine() throws Exception {
        String operator = "caixa-" + uniqueSuffix();
        String warehouseCode = givenWarehouse(uniqueSuffix());
        String[] skus = new String[THREADS];
        for (int i = 0; i < THREADS; i++) {
            skus[i] = givenStockedSku(warehouseCode, uniqueSuffix(), operator);
        }

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 4", operator);

        Outcome outcome = runConcurrently(
                i -> addItem(comanda.id(), skus[i], operator));

        assertThat(outcome.successes())
                .as("mesa aberta, sem disputa de saldo: nenhuma thread pode falhar aqui")
                .isEqualTo(THREADS);
        assertThat(comandaUseCase.getComanda(comanda.id()).items())
                .as("as %d linhas têm que estar todas na comanda: uma faltando é lost update, "
                        + "com o estoque já debitado e o cliente não cobrado", THREADS)
                .hasSize(THREADS);
        // A conta do salão tem que bater com o que foi servido.
        assertThat(comandaUseCase.getComanda(comanda.id()).runningTotal())
                .isEqualByComparingTo(new BigDecimal("25.00").multiply(new BigDecimal(THREADS)));
        // E cada essência tem que ter saído exatamente uma vez.
        for (String sku : skus) {
            assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                    .as("saldo de %s", sku)
                    .isEqualByComparingTo(new BigDecimal(99));
        }
    }

    /**
     * O mesmo sabor pedido por seis atendentes de uma vez — o sábado cheio de verdade. Aqui, além
     * da comanda, as seis disputam a <b>mesma linha</b> de {@code stock_balance}, que é outro
     * agregado e usa trava <b>otimista</b>.
     *
     * <p><b>O teste admite o conflito, mas não o exige.</b> A trava pessimista da comanda
     * serializa os seis lançamentos, e serializados eles tendem a passar todos; se algum ainda
     * perder a corrida otimista do saldo, o resultado correto é {@code STOCK_UPDATE_CONFLICT} —
     * o projeto não tem retry, e {@code PdvSaleConcurrencyIT} já registra essa decisão para a
     * venda de balcão. Escrever `successes == THREADS` aqui amarraria o teste a um detalhe do
     * banco de baixo; o que importa afirmar é a <b>coerência</b>, não o número.</p>
     *
     * <p>É isso que as três asserções finais amarram: linhas, total e saldo, todos os três iguais
     * a {@code successes}. Nenhuma linha some entre as que passaram, e nenhuma essência sai do
     * estoque sem linha correspondente na comanda.</p>
     */
    @Test
    void concurrentAddItem_sameSku_conflictsInsteadOfLosingALine() throws Exception {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator);
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 5", operator);

        Outcome outcome = runConcurrently(
                () -> addItem(comanda.id(), sku, operator));

        assertThat(outcome.total())
                .as("toda thread ou lança ou perde a corrida pelo saldo — nenhuma pode falhar de "
                        + "outro jeito, e a mesa está aberta, então recusa por COMANDA_NOT_OPEN "
                        + "seria bug")
                .isEqualTo(THREADS);
        assertThat(outcome.refusals())
                .as("a mesa fica aberta o tempo todo neste teste")
                .isZero();
        assertThat(outcome.successes())
                .as("ao menos um lançamento tem que passar")
                .isPositive();

        Comanda depois = comandaUseCase.getComanda(comanda.id());
        assertThat(depois.items())
                .as("exatamente as linhas que passaram, nem uma a menos: o que sobrou de %d "
                        + "threads é lost update se não bater", THREADS)
                .hasSize(outcome.successes());
        assertThat(depois.runningTotal())
                .isEqualByComparingTo(new BigDecimal("25.00").multiply(new BigDecimal(outcome.successes())));
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .as("o saldo tem que refletir exatamente os lançamentos confirmados — qualquer "
                        + "outro valor é estoque debitado sem linha na comanda")
                .isEqualByComparingTo(new BigDecimal(100 - outcome.successes()));
    }

    /**
     * A corrida que de fato cobrava dinheiro errado: seis fechamentos simultâneos da mesma mesa.
     * Exatamente <b>um</b> pode gerar pedido; os outros cinco têm que bater em
     * {@code COMANDA_NOT_OPEN}. Sem a trava, mais de um pedido concluído nasceria dos mesmos itens
     * — o cliente cobrado duas vezes e o estoque debitado uma só.
     */
    @Test
    void concurrentClose_onlyOneOrderIsEverGenerated() throws Exception {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator);
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 7", operator);
        addItem(comanda.id(), sku, operator);

        List<PaymentCommand> pagamento =
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("25.00"), null));
        Outcome outcome = runConcurrently(
                () -> comandaUseCase.closeComanda(comanda.id(), pagamento, null, false, operator));

        assertThat(outcome.successes())
                .as("um único fechamento pode passar — dois pedidos concluídos dos mesmos itens "
                        + "seriam o cliente cobrado em dobro")
                .isEqualTo(1);
        assertThat(outcome.refusals())
                .as("as outras %d têm que ser recusadas com COMANDA_NOT_OPEN, não passar em silêncio",
                        THREADS - 1)
                .isEqualTo(THREADS - 1);

        Comanda fechada = comandaUseCase.getComanda(comanda.id());
        assertThat(fechada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(fechada.orderId()).isNotNull();
    }

    /**
     * Fechar e cancelar disputando a mesma mesa. Um dos dois vence — qual, é indiferente e depende
     * do escalonador — mas os dois <b>não</b> podem passar: seria o estoque devolvido de itens que
     * o outro caminho acabou de cobrar.
     */
    @Test
    void concurrentCloseAndCancel_onlyOneWins() throws Exception {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator);
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 9", operator);
        addItem(comanda.id(), sku, operator);

        List<PaymentCommand> pagamento =
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("25.00"), null));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger winners = new AtomicInteger();

        List<Future<?>> futures = List.of(
                executor.submit(() -> attempt(start, winners,
                        () -> comandaUseCase.closeComanda(comanda.id(), pagamento, null, false, operator))),
                executor.submit(() -> attempt(start, winners,
                        () -> comandaUseCase.cancelComanda(comanda.id(), operator))));
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(winners.get())
                .as("fechar e cancelar são terminais e mutuamente exclusivos: exatamente um vence")
                .isEqualTo(1);
        assertThat(comandaUseCase.getComanda(comanda.id()).status())
                .isIn(ComandaStatus.FECHADA, ComandaStatus.CANCELADA);
    }

    private Object attempt(CountDownLatch start, AtomicInteger winners, Runnable action) {
        try {
            start.await();
            action.run();
            winners.incrementAndGet();
        } catch (ComandaNotOpenException e) {
            // Perdeu a corrida — o resultado correto.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
