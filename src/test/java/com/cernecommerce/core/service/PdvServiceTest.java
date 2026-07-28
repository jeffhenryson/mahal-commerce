package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PdvServiceTest {

    /** Carvão do exemplo do plano: custo 18,00, venda 22,00. */
    private static final Pricing CARVAO = Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));

    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("10");

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock EstoqueUseCase estoqueUseCase;

    PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                estoqueUseCase, MAX_DISCOUNT_PERCENT);
    }

    private CashRegisterSession openSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private CashRegisterSession closedSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                Instant.now(), "gerente", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                CashRegisterSession.Status.CLOSED);
    }

    private void givenOpenSessionAndPersistence() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static SaleItemCommand twoCharcoals(BigDecimal discount) {
        return new SaleItemCommand("CARV-001", new BigDecimal("2.000"), discount);
    }

    // ── PDV-F001: abertura e fechamento ──────────────────────────────────────────────────────

    @Test
    void openSession_validatesTheWarehouseBeforeStampingItOnTheSession() {
        when(cashRegisterRepository.findOpenByOperator("caixa1")).thenReturn(Optional.empty());
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashRegisterSession session = pdvService.openSession("caixa1", new BigDecimal("200.00"), "LOJA-01");

        assertThat(session.operator()).isEqualTo("caixa1");
        assertThat(session.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(session.isOpen()).isTrue();
        // Descobrir que o depósito não existe só na primeira venda seria descobrir tarde demais.
        verify(estoqueUseCase).getWarehouseByCode("LOJA-01");
    }

    @Test
    void openSession_refusesASecondOpenSessionForTheSameOperator() {
        when(cashRegisterRepository.findOpenByOperator("caixa1")).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.openSession("caixa1", BigDecimal.TEN, "LOJA-01"))
                .isInstanceOf(CashRegisterSessionAlreadyOpenException.class);

        verify(cashRegisterRepository, never()).save(any());
    }

    @Test
    void getCurrentSession_throwsWhenTheOperatorHasNoOpenRegister() {
        when(cashRegisterRepository.findOpenByOperator("caixa1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.getCurrentSession("caixa1"))
                .isInstanceOf(NoOpenCashRegisterSessionException.class);
    }

    @Test
    void closeSession_computesExpectedFromOpeningSalesAndMovements() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        // abertura 10,00 + vendas 500,00 − sangria líquida 150,00 = 360,00
        when(orderRepository.sumConcludedNetAmountBySessionId(1L)).thenReturn(new BigDecimal("500.00"));
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(new BigDecimal("-150.00"));
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashRegisterSession closed = pdvService.closeSession(1L, new BigDecimal("355.00"), "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("360.00");
        assertThat(closed.countedAmount()).isEqualByComparingTo("355.00");
        assertThat(closed.differenceAmount()).isEqualByComparingTo("-5.00");
        assertThat(closed.diverges()).isTrue();
        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
    }

    @Test
    void closeSession_doesNotRequireBeingTheOwner() {
        // A conferência costuma ser do gerente — daí PDV_SESSION_CLOSE separada de PDV_SESSION_MANAGE.
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.sumConcludedNetAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumSignedAmountBySessionId(1L)).thenReturn(BigDecimal.ZERO);
        when(cashRegisterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(pdvService.closeSession(1L, BigDecimal.TEN, "gerente").closedBy()).isEqualTo("gerente");
    }

    @Test
    void closeSession_refusesAnAlreadyClosedSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closedSession()));

        assertThatThrownBy(() -> pdvService.closeSession(1L, BigDecimal.TEN, "gerente"))
                .isInstanceOf(CashRegisterSessionClosedException.class);

        verify(cashRegisterRepository, never()).save(any());
    }

    // ── PDV-F002: sangria e suprimento ───────────────────────────────────────────────────────

    @Test
    void registerCashMovement_requiresTheSessionToBelongToTheOperator() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.registerCashMovement(1L, CashMovementType.SANGRIA,
                new BigDecimal("50.00"), "cofre", "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);

        verify(cashMovementRepository, never()).save(any());
    }

    @Test
    void registerCashMovement_persistsWhenTheSessionIsOwnAndOpen() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(cashMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashMovement movement = pdvService.registerCashMovement(1L, CashMovementType.SANGRIA,
                new BigDecimal("50.00"), "depósito no cofre", "caixa1");

        assertThat(movement.type()).isEqualTo(CashMovementType.SANGRIA);
        assertThat(movement.amount()).isEqualByComparingTo("50.00");
        assertThat(movement.signedAmount()).isEqualByComparingTo("-50.00");
    }

    @Test
    void registerCashMovement_refusesOnClosedSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closedSession()));

        assertThatThrownBy(() -> pdvService.registerCashMovement(1L, CashMovementType.SUPRIMENTO,
                BigDecimal.TEN, "troco", "caixa1"))
                .isInstanceOf(CashRegisterSessionClosedException.class);
    }

    // ── PDV-C004: a venda herda o depósito e exige posse ─────────────────────────────────────

    @Test
    void registerSale_takesTheWarehouseFromTheSessionNotFromTheCaller() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), "caixa1");

        assertThat(order.warehouseCode()).isEqualTo("LOJA-01");
        verify(estoqueUseCase).adjustStock(any(), eq("LOJA-01"), any(), any(), any(), any());
    }

    @Test
    void registerSale_refusesASessionThatBelongsToAnotherOperator() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Parte D: liquidar no balcão um pedido feito no app ───────────────────────────────────

    /** Pedido montado no aplicativo, com estoque já reservado, esperando pagamento. */
    private static Order pendingOnlineOrder() {
        return Order.of(7L, null, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO, 42L,
                null, "LOJA-01", List.of(com.cernecommerce.core.domain.model.pedido.OrderItem
                        .fromCatalog("CARV-001", new BigDecimal("2.000"), CARVAO, null)),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, Instant.now(), null, null, null, 0L);
    }

    @Test
    void settleOnlineOrder_consumesTheReservationInsteadOfDebitingStockAgain() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        pdvService.settleOnlineOrder(1L, 7L, "caixa1");

        verify(estoqueUseCase).consumeReservationsByOwner("ORDER:7", "caixa1");
        // Dar baixa aqui debitaria a mercadoria duas vezes: ela já saiu do disponível na reserva.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void settleOnlineOrder_keepsTheChannelAndAttachesTheCashSession() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pendingOnlineOrder()));
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order settled = pdvService.settleOnlineOrder(1L, 7L, "caixa1");

        // O canal é a ORIGEM e não muda — senão o relatório de conversão do site mentiria.
        assertThat(settled.channel()).isEqualTo(SalesChannel.MARKETPLACE);
        assertThat(settled.sessionId()).isEqualTo(1L);
        assertThat(settled.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(settled.orderNumber()).isEqualTo("000001001");
        assertThat(settled.paidAt()).isNotNull();
    }

    @Test
    void settleOnlineOrder_refusesAnOrderThatIsNotAwaitingPayment() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findById(7L))
                .thenReturn(Optional.of(pendingOnlineOrder().cancelled("desistiu", Instant.now())));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, "caixa1"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void settleOnlineOrder_refusesASessionThatBelongsToAnotherOperator() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);

        verify(estoqueUseCase, never()).consumeReservationsByOwner(any(), any());
    }

    @Test
    void listPendingOnlineOrders_filtersByChannelAndStatus() {
        when(orderRepository.findAll(SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                null, null, null, 0, 20)).thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        pdvService.listPendingOnlineOrders(0, 20);

        verify(orderRepository).findAll(SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                null, null, null, 0, 20);
    }

    @Test
    void listSessions_delegatesToRepository() {
        PageResult<CashRegisterSession> page = new PageResult<>(List.of(openSession()), 0, 20, 1L, 1);
        when(cashRegisterRepository.findAll(0, 20)).thenReturn(page);

        assertThat(pdvService.listSessions(0, 20).content()).hasSize(1);
    }

    // ── PDV-F004: preço e custo vêm do catálogo ──────────────────────────────────────────────

    @Test
    void registerSale_resolvesPriceAndCostFromTheCatalog() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        StockBalance balance = StockBalance.of(1L, "CARV-001", 2L, new BigDecimal("18.000"), 1L);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(balance);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), "caixa1");

        // 2 x 22,00 — o preço não veio do chamador em lugar nenhum.
        assertThat(order.grossAmount()).isEqualByComparingTo("44.00");
        assertThat(order.items()).singleElement().satisfies(item -> {
            assertThat(item.unitPrice()).isEqualByComparingTo("22.00");
            assertThat(item.costPrice()).isEqualByComparingTo("18.00");
        });
    }

    @Test
    void registerSale_refusesProductWithoutPriceBeforeTouchingStock() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.findPricingBySku("SEM-PRECO")).thenReturn(Pricing.empty());

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(new SaleItemCommand("SEM-PRECO", BigDecimal.ONE, null)), "caixa1"))
                .isInstanceOf(ProductNotPricedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Desconto ─────────────────────────────────────────────────────────────────────────────

    @Test
    void registerSale_appliesDiscountWithinTheLimit() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        // 4,00 sobre 44,00 = 9,09% — abaixo do teto de 10%.
        Order order = pdvService.registerSale(1L, null,
                List.of(twoCharcoals(new BigDecimal("4.00"))), "caixa1");

        assertThat(order.discountAmount()).isEqualByComparingTo("4.00");
        assertThat(order.netAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void registerSale_refusesDiscountAboveTheLimitBeforeTouchingStock() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);

        // 5,00 sobre 44,00 = 11,36% — acima do teto de 10%.
        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(new BigDecimal("5.00"))), "caixa1"))
                .isInstanceOf(DiscountLimitExceededException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Conclusão e numeração ────────────────────────────────────────────────────────────────

    @Test
    void registerSale_concludesInTheSameTransactionAndStampsTheOrderNumber() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), "caixa1");

        assertThat(order.channel()).isEqualTo(SalesChannel.BALCAO);
        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.orderNumber()).isEqualTo("000001000");
        assertThat(order.concludedAt()).isNotNull();
        assertThat(order.paidAt()).isNotNull();
    }

    @Test
    void registerSale_keepsTheCustomerWhenIdentified() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order order = pdvService.registerSale(1L, 42L, List.of(twoCharcoals(null)), "caixa1");

        assertThat(order.customerId()).isEqualTo(42L);
    }

    @Test
    void registerSale_allowsAnonymousSale() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        assertThat(pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), "caixa1")
                .customerId()).isNull();
    }

    // ── Baixa de estoque ─────────────────────────────────────────────────────────────────────

    @Test
    void registerSale_adjustsStockPerItemWithTheSessionInTheReason() {
        givenOpenSessionAndPersistence();
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        pdvService.registerSale(1L, null, List.of(twoCharcoals(null)), "caixa1");

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(new BigDecimal("2.000")), eq("Venda balcão sessão #1"), eq("caixa1"));
    }

    @Test
    void registerSale_throwsWhenSessionNotFound() {
        when(cashRegisterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.registerSale(99L, null,
                List.of(twoCharcoals(null)), "caixa1"))
                .isInstanceOf(CashRegisterSessionNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_throwsWhenSessionIsClosed() {
        CashRegisterSession closed = CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN,
                "LOJA-01", Instant.now(), "gerente", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                CashRegisterSession.Status.CLOSED);
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(null)), "caixa1"))
                .isInstanceOf(CashRegisterSessionClosedException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_propagatesUnknownSkuAndDoesNotSaveOrder() {
        // EST-C002: antes, um SKU digitado errado criava saldo e ledger órfãos e a venda era
        // gravada normalmente. Agora a resolução de preço já barra, antes de tocar o estoque.
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.findPricingBySku("SKU-FANTASMA"))
                .thenThrow(new ProductNotFoundException("SKU-FANTASMA"));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(new SaleItemCommand("SKU-FANTASMA", BigDecimal.ONE, null)), "caixa1"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void registerSale_propagatesInsufficientStockAndDoesNotSaveOrder() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(estoqueUseCase.findPricingBySku("CARV-001")).thenReturn(CARVAO);
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientStockException("CARV-001", 2L, new BigDecimal("1.000"),
                        new BigDecimal("2.000")));

        assertThatThrownBy(() -> pdvService.registerSale(1L, null,
                List.of(twoCharcoals(null)), "caixa1"))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any());
    }

    // ── PDV-F005: leitura ────────────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsThePersistedOrder() {
        Order stored = Order.of(7L, "000001000", SalesChannel.BALCAO, OrderStatus.CONCLUIDO, null, 1L,
                "LOJA-01", List.of(com.cernecommerce.core.domain.model.pedido.OrderItem.of(1L, "CARV-001",
                        new BigDecimal("2.000"), new BigDecimal("22.00"), new BigDecimal("18.00"),
                        BigDecimal.ZERO, null)),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, Instant.now(), Instant.now(), Instant.now(), null, 0L);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(stored));

        assertThat(pdvService.getOrder(7L).orderNumber()).isEqualTo("000001000");
    }

    @Test
    void getOrder_throwsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.getOrder(99L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listSessionOrders_requiresAnExistingSession() {
        when(cashRegisterRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pdvService.listSessionOrders(99L, 0, 20))
                .isInstanceOf(CashRegisterSessionNotFoundException.class);

        verify(orderRepository, never()).findBySessionId(any(), anyInt(), anyInt());
    }

    @Test
    void listSessionOrders_delegatesToRepository() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(openSession()));
        when(orderRepository.findBySessionId(1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        assertThat(pdvService.listSessionOrders(1L, 0, 20).content()).isEmpty();
    }
}
