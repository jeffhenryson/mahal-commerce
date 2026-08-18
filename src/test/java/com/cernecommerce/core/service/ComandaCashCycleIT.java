package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
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

/**
 * Comanda de mesa de ponta a ponta contra banco real (PDV-F009).
 *
 * <p>É o teste que prova que a baixa de estoque é <b>imediata por item</b>, não só no fechamento —
 * ver o javadoc de {@code ComandaService}. Os testes de unidade mockam {@code EstoqueUseCase}, então
 * nada exercitava se o saldo de verdade caía a cada lançamento, nem o mapeamento de
 * {@code comanda}/{@code comanda_item} contra o ciclo completo (abrir → lançar → fechar).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ComandaCashCycleIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Depósito + produto precificado + saldo inicial, mesmo padrão de {@code PdvCashCycleIT}. */
    private String givenStockedWarehouse(String operator) {
        String suffix = uniqueSuffix();
        String warehouseCode = "LOUNGE-" + suffix;
        String sku = "ESS-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", operator);
        return warehouseCode + "|" + sku;
    }

    @Test
    void fullCycle_openAddTwoItemsWithImmediateDebitAndCloseWithSplitPayment() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        flushAndClear();

        // 1. Abre a comanda.
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 4", operator);
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(comanda.warehouseCode()).isEqualTo(warehouseCode);
        flushAndClear();

        // 2. Lança o primeiro item — debita 1 unidade NA HORA, não no fechamento.
        comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("49.000");

        // 3. Lança o segundo item — debita mais 1, mesmo antes de a comanda fechar.
        Comanda comTwoItems = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");
        assertThat(comTwoItems.items()).hasSize(2);
        assertThat(comTwoItems.runningTotal()).isEqualByComparingTo("50.00");

        // 4. Fecha dividindo o pagamento: 15 no débito + 10 em dinheiro para uma comanda de 50 —
        // espera 400 de pagamento insuficiente, então cobre o total exato de duas formas.
        List<PaymentCommand> split = List.of(
                new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("30.00"), null),
                new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null));
        Order order = comandaUseCase.closeComanda(comanda.id(), split, operator);
        flushAndClear();

        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.channel()).isEqualTo(SalesChannel.BALCAO);
        assertThat(order.netAmount()).isEqualByComparingTo("50.00");
        assertThat(order.items()).hasSize(2);
        assertThat(pdvUseCase.getOrderPayments(order.id())).hasSize(2);

        // 5. Sem novo débito no fechamento — o saldo continua exatamente onde os lançamentos
        // incrementais deixaram.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");

        Comanda fechada = comandaUseCase.getComanda(comanda.id());
        assertThat(fechada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(fechada.orderId()).isEqualTo(order.id());
        assertThat(comandaUseCase.listOpenComandas(session.id())).isEmpty();
    }

    @Test
    void cancelComanda_returnsStockForEveryLaunchedItem() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 9", operator);
        comandaUseCase.addItem(comanda.id(), sku, new BigDecimal("2.000"), operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");

        Comanda cancelada = comandaUseCase.cancelComanda(comanda.id(), operator);
        flushAndClear();

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        // Devolveu ao estoque exatamente o que tinha sido debitado — de volta a 50.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("50.000");
        assertThat(comandaUseCase.listOpenComandas(session.id())).isEmpty();
    }
}
