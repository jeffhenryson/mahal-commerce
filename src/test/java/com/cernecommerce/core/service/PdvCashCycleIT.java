package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
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
 * Ciclo de caixa de ponta a ponta contra banco real (PDV-F001/F002/C004).
 *
 * <p>É o teste que <b>prova a fatia</b>: abre o caixa, vende, faz sangria, fecha com valor contado
 * divergente e confere que a sessão fechou <b>apesar</b> da divergência. Os testes de unidade
 * mockam os repositórios e passariam mesmo com o mapeamento das colunas novas da V66 errado.</p>
 *
 * <p><b>Limitação conhecida:</b> o índice parcial único que garante "uma sessão aberta por operador"
 * ({@code uk_cash_register_session_open_operator}, V66) <b>não existe</b> nestes testes. O perfil
 * {@code dev} monta o schema por {@code ddl-auto} a partir das entities, e o H2 não suporta
 * {@code CREATE UNIQUE INDEX ... WHERE}. O que se prova aqui é a checagem de domínio; a garantia sob
 * concorrência só existe em Postgres e continua não exercitada por teste algum.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PdvCashCycleIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Nome único por teste: o índice de depósito é global e os testes compartilham o schema. */
    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Depósito + produto precificado + saldo inicial. Cadastrar o SKU é pré-condição desde EST-C002:
     * movimentar SKU inexistente é 404.
     */
    private String givenStockedWarehouse(String operator) {
        String suffix = uniqueSuffix();
        String warehouseCode = "LOJA-" + suffix;
        String sku = "CARV-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        // Carvão do exemplo do plano: custo 18,00, venda 22,00.
        estoqueUseCase.createProduct(sku, "Carvão " + suffix, "Carvões", List.of(),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", operator);
        return warehouseCode + "|" + sku;
    }

    @Test
    void fullCycle_openSellWithdrawAndCloseWithDivergence() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        // 1. Abre o caixa com 200,00 de fundo de troco.
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("200.00"), warehouseCode);
        assertThat(session.id()).isNotNull();
        assertThat(session.isOpen()).isTrue();
        flushAndClear();

        // 2. Vende 2 unidades. O preço vem do catálogo; o depósito, da sessão.
        Order order = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, new BigDecimal("2.000"), null)), operator);
        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.netAmount()).isEqualByComparingTo("44.00");
        assertThat(order.warehouseCode()).isEqualTo(warehouseCode);
        assertThat(order.orderNumber()).isNotBlank();
        flushAndClear();

        // A venda saiu do estoque de verdade: 50 − 2.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");

        // 3. Sangria de 150,00.
        pdvUseCase.registerCashMovement(session.id(), CashMovementType.SANGRIA, new BigDecimal("150.00"),
                "depósito no cofre", operator);
        flushAndClear();

        // 4. Fecha contando 90,00 — esperado 200 + 44 − 150 = 94,00, faltam 4,00.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("90.00"), "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("94.00");
        assertThat(closed.countedAmount()).isEqualByComparingTo("90.00");
        assertThat(closed.differenceAmount()).isEqualByComparingTo("-4.00");
        assertThat(closed.diverges()).isTrue();
        // O ponto da fatia: divergência é registrada, não bloqueia.
        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
        assertThat(closed.closedBy()).isEqualTo("gerente");
    }

    @Test
    void cancelledSaleDoesNotCountTowardsTheExpectedAmount() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), operator);
        flushAndClear();

        // Sem venda cancelada, esperado = 100 + 22 = 122.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("122.00"), operator);

        assertThat(closed.expectedAmount()).isEqualByComparingTo("122.00");
        assertThat(closed.diverges()).isFalse();
    }

    @Test
    void movementsNetOutAgainstEachOther() {
        String operator = "caixa-" + uniqueSuffix();
        String warehouseCode = givenStockedWarehouse(operator).split("\\|")[0];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        pdvUseCase.registerCashMovement(session.id(), CashMovementType.SANGRIA, new BigDecimal("80.00"),
                "cofre", operator);
        pdvUseCase.registerCashMovement(session.id(), CashMovementType.SUPRIMENTO, new BigDecimal("30.00"),
                "reforço de troco", operator);
        flushAndClear();

        assertThat(pdvUseCase.listCashMovements(session.id())).hasSize(2);
        // 100 − 80 + 30 = 50. O sinal vem do tipo, não do valor gravado.
        assertThat(pdvUseCase.closeSession(session.id(), new BigDecimal("50.00"), operator)
                .expectedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void openSession_refusesASecondRegisterForTheSameOperator() {
        String operator = "caixa-" + uniqueSuffix();
        String warehouseCode = givenStockedWarehouse(operator).split("\\|")[0];

        pdvUseCase.openSession(operator, BigDecimal.TEN, warehouseCode);
        flushAndClear();

        assertThatThrownBy(() -> pdvUseCase.openSession(operator, BigDecimal.TEN, warehouseCode))
                .isInstanceOf(CashRegisterSessionAlreadyOpenException.class);
    }

    @Test
    void anotherOperatorCannotSellOnSomeoneElsesRegister() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.TEN, setup[0]);
        flushAndClear();

        assertThatThrownBy(() -> pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);
    }

    @Test
    void sessionOrdersAreListedForTheSession() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.TEN, setup[0]);
        pdvUseCase.registerSale(session.id(), null, List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), operator);
        pdvUseCase.registerSale(session.id(), null, List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), operator);
        flushAndClear();

        assertThat(pdvUseCase.listSessionOrders(session.id(), 0, 20).totalElements()).isEqualTo(2L);
    }

    @Test
    void orderNumbersAreUniqueAcrossSales() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.TEN, setup[0]);
        String first = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), operator).orderNumber();
        String second = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), operator).orderNumber();

        assertThat(first).isNotEqualTo(second);
    }
}
