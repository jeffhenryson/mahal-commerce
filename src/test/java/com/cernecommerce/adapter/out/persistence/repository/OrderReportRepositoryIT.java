package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Testa {@link OrderReportRepositoryImpl} contra banco real (GET /orders/summary).
 *
 * <p>Mesma razão de {@code PedidoRepositoryIT}: a suíte de unidade de {@code OrderReportService}
 * mocka o repositório, então nada exercitava as queries de agregação nem o {@code LEFT JOIN} por
 * valor entre {@code order_item.sku} e {@code product.sku}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OrderReportRepositoryIT {

    @Autowired OrderRepositoryImpl orderRepository;
    @Autowired OrderReportRepositoryImpl orderReportRepository;
    @Autowired ProductJpaRepository productJpaRepository;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static Pricing carvao() {
        return Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));
    }

    private static List<OrderItem> oneCharcoal(BigDecimal quantity) {
        return List.of(OrderItem.fromCatalog("CARV-001", quantity, carvao(), null));
    }

    private static List<OrderItem> orphanSkuItem() {
        return List.of(OrderItem.fromCatalog("SKU-EXCLUIDO", new BigDecimal("1.000"),
                Pricing.of(new BigDecimal("5.00"), null, new BigDecimal("10.00")), null));
    }

    private void saveProduct(String sku, String name) {
        ProductEntity product = new ProductEntity();
        product.setSku(sku);
        product.setName(name);
        product.setType("SIMPLES");
        product.setUnit("UN");
        product.setActive(true);
        productJpaRepository.save(product);
    }

    @Test
    void summarize_appliesRevenueStatusFilterAndTopProductsJoin() {
        saveProduct("CARV-001", "Carvão em Barra");

        // CONCLUIDO — conta na receita: gross 44, sem desconto.
        orderRepository.save(Order.openBalcao(1L, "LOJA-01", null, oneCharcoal(new BigDecimal("2.000")))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));

        // PAGO — conta na receita: gross 22. Item de SKU órfão junto, sem product correspondente.
        List<OrderItem> paidItems = new java.util.ArrayList<>(oneCharcoal(new BigDecimal("1.000")));
        paidItems.addAll(orphanSkuItem());
        orderRepository.save(Order.openMarketplace(42L, "LOJA-01", paidItems).paid(Instant.now()));

        // CANCELADO — nunca teve pagamento confirmado, não conta na receita nem no topProducts.
        orderRepository.save(Order.openMarketplace(42L, "LOJA-01", oneCharcoal(new BigDecimal("5.000")))
                .cancelled("Desistência", Instant.now()));

        // REEMBOLSADO — teve pagamento confirmado mas o dinheiro voltou, não conta na receita.
        Order refunded = orderRepository.save(Order.openBalcao(1L, "LOJA-01", null, oneCharcoal(new BigDecimal("3.000")))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));
        orderRepository.save(orderRepository.findById(refunded.id()).orElseThrow()
                .refunded("Devolução", Instant.now()));

        flushAndClear();

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now().plusSeconds(3600);
        OrderSummary summary = orderReportRepository.summarize(null, null, null, from, to, 10);

        assertThat(summary.totalOrders()).isEqualTo(4);
        assertThat(summary.ordersByStatus()).containsEntry(OrderStatus.CONCLUIDO, 1L)
                .containsEntry(OrderStatus.PAGO, 1L)
                .containsEntry(OrderStatus.CANCELADO, 1L)
                .containsEntry(OrderStatus.REEMBOLSADO, 1L);

        // Só CONCLUIDO (44.00) e PAGO (22.00 + 10.00 do item órfão) contam — 76.00.
        assertThat(summary.totalRevenueNet()).isEqualByComparingTo("76.00");
        assertThat(summary.cancelledOrRefundedRate()).isEqualByComparingTo("0.5000");

        assertThat(summary.revenueByChannel())
                .containsEntry(SalesChannel.BALCAO, new BigDecimal("44.00"))
                .containsEntry(SalesChannel.MARKETPLACE, new BigDecimal("32.00"));

        assertThat(summary.topProducts()).extracting(OrderSummary.TopProduct::sku,
                        OrderSummary.TopProduct::productName)
                .contains(tuple("CARV-001", "Carvão em Barra"), tuple("SKU-EXCLUIDO", "SKU-EXCLUIDO"));
    }

    @Test
    void findTopProducts_byRevenue_ranksHighestRevenueFirst() {
        saveProduct("CARV-001", "Carvão em Barra");
        saveProduct("ESSE-001", "Essência Premium");

        // CARV-001: 2 unidades a 22.00 = 44.00 de receita, 2 de quantidade.
        orderRepository.save(Order.openBalcao(1L, "LOJA-01", null, oneCharcoal(new BigDecimal("2.000")))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));

        // ESSE-001: 10 unidades a 5.00 = 50.00 de receita, 10 de quantidade — mais receita E mais
        // quantidade que CARV-001, então aparece primeiro nos dois rankings.
        Pricing essencia = Pricing.of(new BigDecimal("3.00"), null, new BigDecimal("5.00"));
        orderRepository.save(Order.openBalcao(1L, "LOJA-01", null,
                        List.of(OrderItem.fromCatalog("ESSE-001", new BigDecimal("10.000"), essencia, null)))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));

        flushAndClear();

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now().plusSeconds(3600);

        List<OrderSummary.TopProduct> byRevenue = orderReportRepository.findTopProducts(null, null, null,
                from, to, 10, false);
        assertThat(byRevenue).extracting(OrderSummary.TopProduct::sku)
                .containsExactly("ESSE-001", "CARV-001");
        assertThat(byRevenue.get(0).revenue()).isEqualByComparingTo("50.00");

        List<OrderSummary.TopProduct> byQuantity = orderReportRepository.findTopProducts(null, null, null,
                from, to, 10, true);
        assertThat(byQuantity).extracting(OrderSummary.TopProduct::sku)
                .containsExactly("ESSE-001", "CARV-001");
        assertThat(byQuantity.get(0).quantitySold()).isEqualByComparingTo("10.000");
    }

    @Test
    void findTopProducts_byQuantity_ordersByQuantityNotRevenue() {
        saveProduct("CARV-001", "Carvão em Barra");
        saveProduct("ESSE-001", "Essência Premium");

        // CARV-001: 1 unidade a 22.00 = 22.00 de receita, só 1 de quantidade.
        orderRepository.save(Order.openBalcao(1L, "LOJA-01", null, oneCharcoal(new BigDecimal("1.000")))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));

        // ESSE-001: 20 unidades a 1.00 = 20.00 de receita (menor que CARV-001), mas quantidade
        // muito maior — inverte o ranking entre os dois modos de ordenação.
        Pricing essencia = Pricing.of(new BigDecimal("0.50"), null, new BigDecimal("1.00"));
        orderRepository.save(Order.openBalcao(1L, "LOJA-01", null,
                        List.of(OrderItem.fromCatalog("ESSE-001", new BigDecimal("20.000"), essencia, null)))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));

        flushAndClear();

        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now().plusSeconds(3600);

        List<OrderSummary.TopProduct> byRevenue = orderReportRepository.findTopProducts(null, null, null,
                from, to, 10, false);
        assertThat(byRevenue).extracting(OrderSummary.TopProduct::sku)
                .containsExactly("CARV-001", "ESSE-001");

        List<OrderSummary.TopProduct> byQuantity = orderReportRepository.findTopProducts(null, null, null,
                from, to, 10, true);
        assertThat(byQuantity).extracting(OrderSummary.TopProduct::sku)
                .containsExactly("ESSE-001", "CARV-001");
    }

    @Test
    void summarize_returnsZeroedSummaryWhenNoOrdersInPeriod() {
        Instant from = Instant.now().minusSeconds(7200);
        Instant to = Instant.now().minusSeconds(3600);

        OrderSummary summary = orderReportRepository.summarize(null, null, null, from, to, 10);

        assertThat(summary.totalOrders()).isZero();
        assertThat(summary.totalRevenueNet()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.cancelledOrRefundedRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.topProducts()).isEmpty();
        assertThat(summary.dailyRevenue()).isEmpty();
    }
}
