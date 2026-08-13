package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
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
import org.springframework.test.context.transaction.TestTransaction;
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

    /**
     * Cria um produto SIMPLES já elegível para entrar como componente de kit — os testes deste
     * arquivo testam a explosão de venda de kit, não a regra de elegibilidade em si.
     */
    private void createEligibleComponent(String sku, String name, String category, Pricing pricing) {
        estoqueUseCase.createProduct(sku, name, category, List.of(), pricing, null, null, false, false, null, null,
                List.of(), List.of(), null, null, null, false, true, null, null);
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
        createEligibleComponent(carvaoSku, "Carvão " + suffix, "carvao",
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")));
        createEligibleComponent(essenciaSku, "Essência " + suffix, "essencia",
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
        createEligibleComponent(componentSku, "Carvão " + suffix, "carvao",
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")));
        estoqueUseCase.createProduct(kitSku, "Kit " + suffix, "combo", List.of(),
                Pricing.of(null, null, new BigDecimal("25.00")));
        estoqueUseCase.defineKitRecipe(kitSku, List.of(new KitComponentCommand(componentSku, BigDecimal.ONE)));
        flushAndClear();

        assertThatThrownBy(() -> estoqueUseCase.adjustStock(kitSku, warehouseCode, MovementType.AJUSTE,
                        BigDecimal.TEN, "contagem", operator))
                .isInstanceOf(com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException.class);
    }

    @Test
    void kitSale_withInsufficientStockInOneComponent_rollsBackAllComponentMovements() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String warehouseCode = "LOJA-" + suffix;
        String componentASku = "COMPA-" + suffix;
        String componentBSku = "COMPB-" + suffix;
        String kitSku = "KIT-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        createEligibleComponent(componentASku, "Componente A " + suffix, "componente",
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00")));
        createEligibleComponent(componentBSku, "Componente B " + suffix, "componente",
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        // Componente A tem saldo confortável (10 un.); componente B tem saldo insuficiente para
        // o que a receita do kit vai exigir por unidade vendida (5 un., só 1 disponível).
        estoqueUseCase.adjustStock(componentASku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);
        estoqueUseCase.adjustStock(componentBSku, warehouseCode, MovementType.ENTRADA, new BigDecimal("1.000"),
                "carga inicial", operator);

        estoqueUseCase.createProduct(kitSku, "Kit Incompleto " + suffix, "combo", List.of(),
                Pricing.of(null, null, new BigDecimal("50.00")));
        estoqueUseCase.defineKitRecipe(kitSku, List.of(
                new KitComponentCommand(componentASku, BigDecimal.ONE),
                new KitComponentCommand(componentBSku, new BigDecimal("5"))));
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        flushAndClear();

        // A classe é @Transactional no molde de teste do Spring: todo o método roda dentro de UMA
        // transação, revertida automaticamente no fim. Se a venda que vai falhar rodasse nessa
        // MESMA transação, marcá-la como rollback-only não desfaz de verdade o que já foi
        // persistido — o rollback físico só acontece no fim do teste, e uma leitura ANTES disso,
        // ainda dentro da mesma transação, enxerga a baixa parcial do componente A (flush sem
        // commit continua visível na própria transação). Por isso o setup acima é commitado de
        // verdade aqui, e a venda que vai falhar roda na PRÓPRIA transação, fechada logo depois —
        // só assim dá pra observar o rollback físico que o @Transactional de registerSale garante
        // em produção (onde não há transação de teste por cima).
        TestTransaction.flagForCommit();
        TestTransaction.end();

        TestTransaction.start();
        // A ordem em que explodeKitMovement processa os componentes da receita NÃO é garantida
        // (ProductKitComponentEntity não tem @OrderBy, e KitComponentRepositoryImpl#findByKitSku
        // delega num findByKitSku derivado do Spring Data sem ORDER BY) — mas a asserção abaixo
        // não depende disso: seja qual for a ordem, a venda inteira do kit tem que falhar e
        // NENHUM componente pode ficar com baixa parcial persistida.
        assertThatThrownBy(() -> pdvUseCase.registerSale(session.id(), null,
                        List.of(new SaleItemCommand(kitSku, BigDecimal.ONE, null)),
                        List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("50.00"), null)), operator))
                .isInstanceOf(InsufficientStockException.class);
        // Não chama flagForCommit(): a transação já está marcada rollback-only pela exceção, e o
        // end() abaixo a fecha de verdade agora — não só no fim do teste — desfazendo qualquer
        // baixa parcial que tenha sido persistida antes do componente que estourou o saldo.
        TestTransaction.end();

        TestTransaction.start();
        // Nenhuma baixa parcial: componente A continua EXATAMENTE com o saldo original, mesmo que
        // tenha sido processado (e uma SAIDA persistida na mesma transação) antes do componente B
        // estourar o saldo — prova que o @Transactional da operação reverteu TODOS os componentes,
        // não só o que efetivamente lançou a exceção.
        BigDecimal componentABalance = estoqueUseCase.getStockBalance(componentASku, warehouseCode).quantity();
        BigDecimal componentBBalance = estoqueUseCase.getStockBalance(componentBSku, warehouseCode).quantity();
        assertThat(componentABalance).isEqualByComparingTo("10.000");
        assertThat(componentBBalance).isEqualByComparingTo("1.000");
    }
}
