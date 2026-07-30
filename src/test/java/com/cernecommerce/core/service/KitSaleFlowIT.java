package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase.KitComponentCommand;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Venda e reembolso de um kit virtual (EST-F015, §2.10), de ponta a ponta contra banco real:
 * o kit nunca ganha linha própria em {@code stock_balance}, a venda explode em uma movimentação
 * por componente, e o custo do kit é sempre derivado da soma dos componentes.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class KitSaleFlowIT {

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired OrderUseCase orderUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registerSale_ofAKit_explodesIntoComponentMovementsAndDerivesBalanceAndCost() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String warehouseCode = "LOJA-" + suffix;
        String carvaoSku = "CARV-" + suffix;
        String essenciaSku = "ESS-" + suffix;
        String kitSku = "KIT-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(carvaoSku, "Carvão " + suffix, "carvao", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")));
        estoqueUseCase.createProduct(essenciaSku, "Essência " + suffix, "essencia", List.of(),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        estoqueUseCase.adjustStock(carvaoSku, warehouseCode, MovementType.ENTRADA, new BigDecimal("20.000"),
                "carga inicial", operator);
        estoqueUseCase.adjustStock(essenciaSku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);

        estoqueUseCase.createProduct(kitSku, "Kit Narguile " + suffix, "combo", List.of(),
                Pricing.of(null, null, new BigDecimal("45.00")));
        estoqueUseCase.defineKitRecipe(kitSku, List.of(
                new KitComponentCommand(carvaoSku, new BigDecimal("2")),
                new KitComponentCommand(essenciaSku, BigDecimal.ONE)));
        flushAndClear();

        // Custo derivado: 10,00 x 2 (carvão) + 15,00 x 1 (essência) = 35,00.
        Pricing kitPricing = estoqueUseCase.findPricingBySku(kitSku);
        assertThat(kitPricing.costPrice()).isEqualByComparingTo("35.00");
        assertThat(kitPricing.salePrice()).isEqualByComparingTo("45.00");
        assertThat(kitPricing.marginAmount()).isEqualByComparingTo("10.00");

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(kitSku, BigDecimal.ONE, null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("45.00"), null)), operator);
        flushAndClear();

        // Explosão: 1 kit vendido consome 2 unidades de carvão e 1 de essência.
        assertThat(estoqueUseCase.getStockBalance(carvaoSku, warehouseCode).quantity())
                .isEqualByComparingTo("18.000");
        assertThat(estoqueUseCase.getStockBalance(essenciaSku, warehouseCode).quantity())
                .isEqualByComparingTo("9.000");

        // Kit nunca ganha saldo próprio: min(floor(18/2), floor(9/1)) = min(9, 9) = 9.
        assertThat(estoqueUseCase.getStockBalance(kitSku, warehouseCode).quantity())
                .isEqualByComparingTo("9");

        List<StockMovement> carvaoMovements = estoqueUseCase.listMovements(carvaoSku, warehouseCode, 0, 10).content();
        assertThat(carvaoMovements).anySatisfy(m -> {
            assertThat(m.type()).isEqualTo(MovementType.SAIDA);
            assertThat(m.quantity()).isEqualByComparingTo("2.000");
            assertThat(m.reason()).contains("(kit " + kitSku + ")");
        });

        Order refunded = orderUseCase.refundOrder(order.id(), "devolução no prazo", operator);
        flushAndClear();

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        // Estorno simétrico: os componentes voltam ao nível anterior à venda.
        assertThat(estoqueUseCase.getStockBalance(carvaoSku, warehouseCode).quantity())
                .isEqualByComparingTo("20.000");
        assertThat(estoqueUseCase.getStockBalance(essenciaSku, warehouseCode).quantity())
                .isEqualByComparingTo("10.000");
    }

    @Test
    void adjustStock_directAdjustmentOnKit_isRejected() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String warehouseCode = "LOJA-" + suffix;
        String componentSku = "CARV-" + suffix;
        String kitSku = "KIT-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(componentSku, "Carvão " + suffix, "carvao", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")));
        estoqueUseCase.createProduct(kitSku, "Kit " + suffix, "combo", List.of(),
                Pricing.of(null, null, new BigDecimal("25.00")));
        estoqueUseCase.defineKitRecipe(kitSku, List.of(new KitComponentCommand(componentSku, BigDecimal.ONE)));
        flushAndClear();

        assertThatThrownBy(() -> estoqueUseCase.adjustStock(kitSku, warehouseCode, MovementType.AJUSTE,
                        BigDecimal.TEN, "contagem", operator))
                .isInstanceOf(com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException.class);
    }
}
