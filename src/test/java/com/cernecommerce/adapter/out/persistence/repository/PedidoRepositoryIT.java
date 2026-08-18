package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o adapter de persistência de pedidos contra banco real (PDV-F003/F004/F005).
 *
 * <p><b>O que este IT existe para provar:</b> a suíte de unidade mocka o {@code OrderRepository},
 * então nada exercitava o mapeamento domínio↔entidade das colunas novas da V65 nem — o mais
 * arriscado — a numeração por sequência. {@code nextOrderNumber()} é uma query nativa
 * ({@code SELECT nextval('order_number_seq')}); em {@code hml}/{@code prod} a sequência vem da V65,
 * em {@code dev} vem de {@code db/dev/dev-schema.sql}, e um teste que mocka o repositório passaria
 * mesmo que o dialeto recusasse a chamada.</p>
 *
 * <p><b>Por que não é um {@code @DataJpaTest}:</b> mesma razão de {@code EstoqueRepositoryIT} — no
 * Spring Boot 4 as slices saíram para módulos por tecnologia e o artefato não está no classpath.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PedidoRepositoryIT {

    @Autowired OrderRepositoryImpl orderRepository;

    @PersistenceContext EntityManager em;

    /** Força a ida ao banco: sem isso a releitura viria do cache de primeiro nível. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Carvão do exemplo do plano: custo 18,00, venda 22,00. */
    private static Pricing carvao() {
        return Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));
    }

    private static List<OrderItem> twoCharcoals(BigDecimal discount) {
        return List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), carvao(), discount));
    }

    // ── Numeração ────────────────────────────────────────────────────────────────────────────

    @Test
    void nextOrderNumber_worksOnTheConfiguredDialect() {
        String number = orderRepository.nextOrderNumber();

        assertThat(number).isNotBlank().matches("\\d{9}");
    }

    @Test
    void nextOrderNumber_neverRepeats() {
        // Duas chamadas consecutivas têm que devolver valores diferentes — é o ponto da sequência.
        assertThat(orderRepository.nextOrderNumber()).isNotEqualTo(orderRepository.nextOrderNumber());
    }

    // ── Mapeamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void save_persistsEveryFrozenValueOfTheItem() {
        Order saved = orderRepository.save(concludedBalcao(twoCharcoals(new BigDecimal("4.00"))));
        flushAndClear();

        Order reloaded = orderRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("CARV-001");
            assertThat(item.quantity()).isEqualByComparingTo("2.000");
            assertThat(item.unitPrice()).isEqualByComparingTo("22.00");
            // O snapshot de custo é o campo mais caro de retrofitar: se o mapeamento o perder,
            // toda margem histórica passa a ser recalculada com o custo de hoje.
            assertThat(item.costPrice()).isEqualByComparingTo("18.00");
            assertThat(item.discountAmount()).isEqualByComparingTo("4.00");
        });
    }

    @Test
    void save_persistsHeaderTotalsAndTimestamps() {
        Order saved = orderRepository.save(concludedBalcao(twoCharcoals(new BigDecimal("4.00"))));
        flushAndClear();

        Order reloaded = orderRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.channel()).isEqualTo(SalesChannel.BALCAO);
        assertThat(reloaded.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(reloaded.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(reloaded.discountAmount()).isEqualByComparingTo("4.00");
        assertThat(reloaded.netAmount()).isEqualByComparingTo("40.00");
        assertThat(reloaded.orderNumber()).isNotBlank();
        assertThat(reloaded.concludedAt()).isNotNull();
        assertThat(reloaded.cancelledAt()).isNull();
    }

    @Test
    void save_persistsCashbackPercentWhenStamped() {
        List<OrderItem> stamped = List.of(
                OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), carvao(), null)
                        .withCashbackPercent(new BigDecimal("2.5000")));

        Order saved = orderRepository.save(concludedBalcao(stamped));
        flushAndClear();

        OrderItem reloaded = orderRepository.findById(saved.id()).orElseThrow().items().getFirst();

        // Escala 4: é input de fórmula, não valor de exibição.
        assertThat(reloaded.cashbackPercent()).isEqualByComparingTo("2.5000");
        assertThat(reloaded.cashbackAmount()).isEqualByComparingTo("1.10");
    }

    @Test
    void save_keepsCostAndCashbackNullWhenTheyWereNeverKnown() {
        // Pedido legado: um default zero mentiria sobre a margem. Nulo diz "não se sabe".
        List<OrderItem> legacy = List.of(OrderItem.of(null, "CARV-001", new BigDecimal("2.000"),
                new BigDecimal("22.00"), null, BigDecimal.ZERO, null));

        Order saved = orderRepository.save(concludedBalcao(legacy));
        flushAndClear();

        OrderItem reloaded = orderRepository.findById(saved.id()).orElseThrow().items().getFirst();

        assertThat(reloaded.costPrice()).isNull();
        assertThat(reloaded.cashbackPercent()).isNull();
        assertThat(reloaded.marginAmount()).isNull();
    }

    // ── Leitura (PDV-F005) ───────────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmptyForUnknownId() {
        assertThat(orderRepository.findById(999_999L)).isEmpty();
    }

    @Test
    void findBySessionId_returnsMostRecentFirst() {
        orderRepository.save(concludedBalcao(twoCharcoals(null)));
        Order second = orderRepository.save(concludedBalcao(twoCharcoals(null)));
        flushAndClear();

        PageResult<Order> page = orderRepository.findBySessionId(1L, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().getFirst().id()).isEqualTo(second.id());
        assertThat(page.totalElements()).isEqualTo(2L);
    }

    @Test
    void findBySessionId_isolatesSessions() {
        orderRepository.save(concludedBalcao(twoCharcoals(null)));
        flushAndClear();

        assertThat(orderRepository.findBySessionId(999L, 0, 20).content()).isEmpty();
    }

    // ── Filtros de GET /orders (regressão do bug de tipagem de from/to nulos) ─────────────────

    @Test
    void findAll_toleratesEveryFilterNull() {
        orderRepository.save(concludedBalcao(twoCharcoals(null)));
        flushAndClear();

        // É exatamente o cenário do bug: from/to Instant nulos sem cast faziam o Postgres real
        // recusar inferir o tipo do bind ("could not determine data type of parameter").
        PageResult<Order> page = orderRepository.findAll(null, null, null, null, null, 0, 20);

        assertThat(page.content()).isNotEmpty();
    }

    @Test
    void findAll_toleratesOnlyFromFilled() {
        orderRepository.save(concludedBalcao(twoCharcoals(null)));
        flushAndClear();

        PageResult<Order> page = orderRepository.findAll(null, null, null, Instant.now().minusSeconds(60), null, 0, 20);

        assertThat(page.content()).isNotEmpty();
    }

    @Test
    void findAll_toleratesOnlyToFilled() {
        orderRepository.save(concludedBalcao(twoCharcoals(null)));
        flushAndClear();

        PageResult<Order> page = orderRepository.findAll(null, null, null, null, Instant.now().plusSeconds(60), 0, 20);

        assertThat(page.content()).isNotEmpty();
    }

    // ── Canal ────────────────────────────────────────────────────────────────────────────────

    @Test
    void save_acceptsMarketplaceOrderSettledAtTheCounter() {
        // O pedido montado no app e pago na loja: continua MARKETPLACE, mas carrega a sessão de
        // caixa que o liquidou. É a invariante relaxada na Fatia 1.
        Order settled = Order.of(null, null, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                42L, 1L, "LOJA-01", twoCharcoals(null), new BigDecimal("44.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("44.00"), null, null, Instant.now(), null, null, null,
                null, 0L);

        Order saved = orderRepository.save(settled);
        flushAndClear();

        Order reloaded = orderRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.channel()).isEqualTo(SalesChannel.MARKETPLACE);
        assertThat(reloaded.sessionId()).isEqualTo(1L);
        assertThat(reloaded.customerId()).isEqualTo(42L);
    }

    private Order concludedBalcao(List<OrderItem> items) {
        return Order.openBalcao(1L, "LOJA-01", null, items)
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now());
    }

    private Order reservedBalcao(List<OrderItem> items) {
        return Order.openBalcao(1L, "LOJA-01", null, items)
                .reserved(orderRepository.nextOrderNumber(), null, Instant.now());
    }

    // ── PDV-F008 — reserva para retirada ────────────────────────────────────────────────────

    @Test
    void save_persistsReservedAtAndSurvivesReload() {
        Order saved = orderRepository.save(reservedBalcao(twoCharcoals(null)));
        flushAndClear();

        Order reloaded = orderRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(OrderStatus.RESERVADO);
        assertThat(reloaded.reservedAt()).isNotNull();
        assertThat(reloaded.concludedAt()).as("retirada ainda não aconteceu").isNull();
        assertThat(reloaded.orderNumber()).isNotBlank();
    }

    @Test
    void save_pickedUp_keepsReservedAtAsHistoryAfterConcluido() {
        Order saved = orderRepository.save(reservedBalcao(twoCharcoals(null)));
        flushAndClear();
        Order reloaded = orderRepository.findById(saved.id()).orElseThrow();

        Order pickedUp = orderRepository.save(reloaded.pickedUp(Instant.now()));
        flushAndClear();
        Order reloadedAfterPickup = orderRepository.findById(pickedUp.id()).orElseThrow();

        assertThat(reloadedAfterPickup.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(reloadedAfterPickup.concludedAt()).isNotNull();
        assertThat(reloadedAfterPickup.reservedAt())
                .as("reservedAt permanece preenchido depois da retirada, como histórico")
                .isEqualTo(reloaded.reservedAt());
    }

    @Test
    void findAll_filtraPorStatusReservado() {
        orderRepository.save(reservedBalcao(twoCharcoals(null)));
        orderRepository.save(concludedBalcao(twoCharcoals(null)));
        flushAndClear();

        PageResult<Order> page = orderRepository.findAll(SalesChannel.BALCAO, OrderStatus.RESERVADO, null,
                null, null, 0, 20);

        assertThat(page.content()).allSatisfy(order -> assertThat(order.status()).isEqualTo(OrderStatus.RESERVADO));
        assertThat(page.content()).isNotEmpty();
    }

    // ── productName no item do pedido (BACKEND_TODO.md do mahal-admin) ────────────────────────

    @Test
    void save_persistsProductNameAndSurvivesReload() {
        List<OrderItem> items = List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), carvao(),
                null, "Carvão em Barra"));
        Order saved = orderRepository.save(concludedBalcao(items));
        flushAndClear();

        Order reloaded = orderRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.items()).singleElement()
                .extracting(OrderItem::productName).isEqualTo("Carvão em Barra");
    }

    // ── Timestamps por etapa da esteira (BACKEND_TODO.md do mahal-admin) ──────────────────────

    @Test
    void save_persistsEsteiraTimestampsAndSurvivesReload() {
        Order marketplace = Order.openMarketplace(1L, "LOJA-01", twoCharcoals(null));
        Order saved = orderRepository.save(marketplace);
        flushAndClear();

        Order paid = orderRepository.save(orderRepository.findById(saved.id()).orElseThrow().paid(Instant.now()));
        Order separado = orderRepository.save(paid.withStatus(OrderStatus.SEPARADO));
        flushAndClear();

        Order reloaded = orderRepository.findById(separado.id()).orElseThrow();

        assertThat(reloaded.status()).isEqualTo(OrderStatus.SEPARADO);
        assertThat(reloaded.separatedAt()).isNotNull();
        assertThat(reloaded.shippedAt()).isNull();
        assertThat(reloaded.deliveredAt()).isNull();
    }
}
