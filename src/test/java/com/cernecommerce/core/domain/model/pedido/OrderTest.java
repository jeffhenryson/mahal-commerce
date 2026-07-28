package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    private static Pricing carvao() {
        return Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));
    }

    private static List<OrderItem> twoCharcoals() {
        return List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), carvao(), null));
    }

    private static Order balcao() {
        return Order.openBalcao(1L, "LOJA-01", null, twoCharcoals());
    }

    // ── Criação ──────────────────────────────────────────────────────────────────────────────

    @Test
    void openBalcao_startsInCriadoAndSumsTotalsFromItems() {
        Order order = balcao();

        assertThat(order.id()).isNull();
        assertThat(order.orderNumber()).isNull();
        assertThat(order.channel()).isEqualTo(SalesChannel.BALCAO);
        assertThat(order.status()).isEqualTo(OrderStatus.CRIADO);
        assertThat(order.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(order.discountAmount()).isEqualByComparingTo("0");
        assertThat(order.netAmount()).isEqualByComparingTo("44.00");
        assertThat(order.createdAt()).isNotNull();
    }

    @Test
    void openBalcao_allowsAnonymousCustomer() {
        // A venda de passagem sem identificação é a maioria do balcão.
        assertThat(balcao().customerId()).isNull();
    }

    @Test
    void openMarketplace_startsWaitingForPayment() {
        Order order = Order.openMarketplace(7L, "LOJA-01", twoCharcoals());

        assertThat(order.channel()).isEqualTo(SalesChannel.MARKETPLACE);
        assertThat(order.status()).isEqualTo(OrderStatus.AGUARDANDO_PAGAMENTO);
        assertThat(order.customerId()).isEqualTo(7L);
        assertThat(order.sessionId()).isNull();
    }

    @Test
    void discountOnItemsFlowsIntoTheOrderTotals() {
        List<OrderItem> items = List.of(
                OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), carvao(), new BigDecimal("4.00")));

        Order order = Order.openBalcao(1L, "LOJA-01", null, items);

        assertThat(order.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(order.discountAmount()).isEqualByComparingTo("4.00");
        assertThat(order.netAmount()).isEqualByComparingTo("40.00");
    }

    // ── Invariantes de canal ─────────────────────────────────────────────────────────────────

    @Test
    void marketplaceRequiresCustomer() {
        assertThatThrownBy(() -> Order.openMarketplace(null, "LOJA-01", twoCharcoals()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("customerId");
    }

    @Test
    void balcaoRequiresSession() {
        assertThatThrownBy(() -> Order.openBalcao(null, "LOJA-01", null, twoCharcoals()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionId");
    }

    @Test
    void marketplaceRejectsCashRegisterSession() {
        assertThatThrownBy(() -> Order.of(null, null, SalesChannel.MARKETPLACE,
                OrderStatus.AGUARDANDO_PAGAMENTO, 7L, 1L, "LOJA-01", twoCharcoals(),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, NOW, null, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessão de caixa");
    }

    @Test
    void rejectsEmptyItemsAndBlankWarehouse() {
        assertThatThrownBy(() -> Order.openBalcao(1L, "LOJA-01", null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Order.openBalcao(1L, "  ", null, twoCharcoals()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsItemWithoutFrozenPriceInANewOrder() {
        List<OrderItem> legacy = List.of(
                OrderItem.of(1L, "CARV-001", new BigDecimal("2.000"), null, null, null, null));

        assertThatThrownBy(() -> Order.openBalcao(1L, "LOJA-01", null, legacy))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem preço congelado");
    }

    @Test
    void rejectsNetAmountThatDoesNotMatchTheOtherTotals() {
        assertThatThrownBy(() -> Order.of(1L, "000001", SalesChannel.BALCAO, OrderStatus.CONCLUIDO,
                null, 1L, "LOJA-01", twoCharcoals(),
                new BigDecimal("44.00"), new BigDecimal("4.00"), BigDecimal.ZERO,
                new BigDecimal("44.00"), // deveria ser 40,00
                null, null, NOW, NOW, NOW, null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("netAmount");
    }

    @Test
    void cancelledStatusAndTimestampMustAgree() {
        assertThatThrownBy(() -> Order.of(1L, "000001", SalesChannel.BALCAO, OrderStatus.CANCELADO,
                null, 1L, "LOJA-01", twoCharcoals(), new BigDecimal("44.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("44.00"), null, null, NOW, null, null,
                null, // CANCELADO sem cancelledAt
                0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancelledAt");
    }

    @Test
    void changeAmountOnlyExistsInBalcao() {
        assertThatThrownBy(() -> Order.of(1L, "000001", SalesChannel.MARKETPLACE, OrderStatus.PAGO,
                7L, null, "LOJA-01", twoCharcoals(), new BigDecimal("44.00"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("44.00"), new BigDecimal("5.00"), null, NOW, NOW,
                null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changeAmount");
    }

    // ── Transições ───────────────────────────────────────────────────────────────────────────

    @Test
    void concluded_stampsNumberAndTimestamp() {
        Order concluded = balcao().concluded("000042", new BigDecimal("6.00"), NOW);

        assertThat(concluded.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(concluded.orderNumber()).isEqualTo("000042");
        assertThat(concluded.concludedAt()).isEqualTo(NOW);
        assertThat(concluded.changeAmount()).isEqualByComparingTo("6.00");
    }

    @Test
    void concluded_stampsPaidAtWhenTheCounterSettlesInOneShot() {
        // No balcão o dinheiro entra junto com a mercadoria saindo.
        assertThat(balcao().concluded("000042", null, NOW).paidAt()).isEqualTo(NOW);
    }

    @Test
    void concluded_requiresOrderNumber() {
        assertThatThrownBy(() -> balcao().concluded("  ", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderNumber");
    }

    @Test
    void marketplaceGoesThroughTheFullFulfillmentChain() {
        Order order = Order.openMarketplace(7L, "LOJA-01", twoCharcoals())
                .paid(NOW)
                .withStatus(OrderStatus.SEPARADO)
                .withStatus(OrderStatus.ENVIADO)
                .withStatus(OrderStatus.ENTREGUE);

        assertThat(order.status()).isEqualTo(OrderStatus.ENTREGUE);
        assertThat(order.paidAt()).isEqualTo(NOW);
    }

    @Test
    void cancelled_recordsReasonAndTimestamp() {
        Order cancelled = balcao().concluded("000042", null, NOW)
                .cancelled("cliente desistiu", NOW);

        assertThat(cancelled.isCancelled()).isTrue();
        assertThat(cancelled.cancelReason()).isEqualTo("cliente desistiu");
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
    }

    @Test
    void cannotCancelTwice() {
        Order cancelled = balcao().cancelled("engano", NOW);

        assertThatThrownBy(() -> cancelled.cancelled("de novo", NOW))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void cannotSkipStatesInTheFulfillmentChain() {
        Order paid = Order.openMarketplace(7L, "LOJA-01", twoCharcoals()).paid(NOW);

        assertThatThrownBy(() -> paid.withStatus(OrderStatus.ENVIADO))
                .isInstanceOf(InvalidOrderStatusTransitionException.class)
                .hasMessageContaining("SEPARADO");
    }

    @Test
    void counterSaleCannotBePaidThroughTheMarketplacePath() {
        assertThatThrownBy(() -> balcao().paid(NOW))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    // ── Cashback e cliente ───────────────────────────────────────────────────────────────────

    @Test
    void withCashbackRedeemed_reducesTheNetWithoutTouchingGross() {
        Order order = balcao().withCashbackRedeemed(new BigDecimal("10.00"));

        assertThat(order.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(order.cashbackRedeemed()).isEqualByComparingTo("10.00");
        assertThat(order.netAmount()).isEqualByComparingTo("34.00");
    }

    @Test
    void totalCashbackEarned_sumsStampedItemsAndIgnoresTheRest() {
        List<OrderItem> items = List.of(
                OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), carvao(), null)
                        .withCashbackPercent(new BigDecimal("2.5000")),
                OrderItem.fromCatalog("ESS-001", BigDecimal.ONE,
                        Pricing.of(new BigDecimal("25.00"), null, new BigDecimal("75.00")), null));

        Order order = Order.openBalcao(1L, "LOJA-01", 7L, items);

        // 2,5% de 44,00 = 1,10; a essência ainda não tem taxa carimbada e não entra.
        assertThat(order.totalCashbackEarned()).isEqualByComparingTo("1.10");
    }

    @Test
    void withCustomer_identifiesTheBuyerAfterTheCartIsBuilt() {
        Order order = balcao().withCustomer(7L);

        assertThat(order.customerId()).isEqualTo(7L);
    }

    @Test
    void itemsAreDefensivelyCopied() {
        List<OrderItem> mutable = new java.util.ArrayList<>(twoCharcoals());
        Order order = Order.openBalcao(1L, "LOJA-01", null, mutable);

        mutable.clear();

        assertThat(order.items()).hasSize(1);
    }
}
