package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
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
class ComandaServiceTest {

    private static final Pricing ESSENCIA = Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00"));

    @Mock ComandaRepository comandaRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PdvService pdvService;

    ComandaService comandaService;

    @BeforeEach
    void setUp() {
        comandaService = new ComandaService(comandaRepository, estoqueUseCase, orderRepository,
                orderPaymentRepository, cashbackUseCase, pdvService);
    }

    private CashRegisterSession openSession() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private Comanda abertaComanda(ComandaItem... items) {
        Comanda comanda = Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1");
        for (ComandaItem item : items) {
            comanda = comanda.withAddedItem(item);
        }
        return Comanda.of(10L, comanda.sessionId(), comanda.warehouseCode(), comanda.tableOrCustomerLabel(),
                comanda.status(), comanda.items(), comanda.orderId(), comanda.openedBy(), comanda.openedAt(),
                comanda.closedAt());
    }

    private static ComandaItem essenciaItem() {
        return ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE, ESSENCIA, "Essência Menta");
    }

    private void givenOrderPersistenceAssignsId() {
        when(orderRepository.nextOrderNumber()).thenReturn("000001000");
        when(orderRepository.save(any())).thenAnswer(inv -> {
            Order arg = inv.getArgument(0);
            return Order.of(500L, arg.orderNumber(), arg.channel(), arg.status(), arg.customerId(),
                    arg.sessionId(), arg.warehouseCode(), arg.items(), arg.grossAmount(), arg.discountAmount(),
                    arg.cashbackRedeemed(), arg.netAmount(), arg.changeAmount(), arg.cancelReason(),
                    arg.createdAt(), arg.paidAt(), arg.concludedAt(), arg.cancelledAt(), arg.refundedAt(),
                    arg.reservedAt(), arg.version());
        });
    }

    // ── Abertura ─────────────────────────────────────────────────────────────────────────────

    @Test
    void openComanda_opensAtTheSessionWarehouse() {
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda comanda = comandaService.openComanda(1L, "Mesa 4", "caixa1");

        assertThat(comanda.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        verify(pdvService).requireOwnOpenSession(1L, "caixa1");
    }

    @Test
    void openComanda_propagatesOwnershipFailureAndDoesNotSave() {
        when(pdvService.requireOwnOpenSession(1L, "outro-operador"))
                .thenThrow(new com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException(1L, "outro-operador"));

        assertThatThrownBy(() -> comandaService.openComanda(1L, "Mesa 4", "outro-operador"))
                .isInstanceOf(com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Lançamento de item ───────────────────────────────────────────────────────────────────

    @Test
    void addItem_resolvesPriceFromCatalogAndDebitsStockImmediately() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda updated = comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1");

        assertThat(updated.items()).hasSize(1);
        assertThat(updated.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        verify(estoqueUseCase).adjustStock(eq("ESS-MENTA"), eq("LOJA-01"), eq(MovementType.SAIDA),
                eq(BigDecimal.ONE), eq("Comanda #10"), eq("caixa1"));
    }

    @Test
    void addItem_refusesOnNonAbertaComandaAndDoesNotTouchStock() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(essenciaItem()), 500L, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void addItem_throwsWhenComandaNotFound() {
        when(comandaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.addItem(999L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ComandaNotFoundException.class);
        verifyNoInteractions(estoqueUseCase);
    }

    @Test
    void addItem_propagatesUnknownSkuAndDoesNotSave() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("DESCONHECIDO")).thenThrow(new ProductNotFoundException("DESCONHECIDO"));

        assertThatThrownBy(() -> comandaService.addItem(10L, "DESCONHECIDO", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(comandaRepository, never()).save(any());
    }

    @Test
    void addItem_propagatesInsufficientStockAndDoesNotSave() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(estoqueUseCase.resolveSaleInfo("ESS-MENTA"))
                .thenReturn(new EstoqueUseCase.CatalogSaleInfo("Essência Menta", ESSENCIA));
        doThrow(new InsufficientStockException("ESS-MENTA", 1L, BigDecimal.ZERO, BigDecimal.ONE))
                .when(estoqueUseCase).adjustStock(any(), any(), any(), any(), any(), any());

        assertThatThrownBy(() -> comandaService.addItem(10L, "ESS-MENTA", BigDecimal.ONE, "caixa1"))
                .isInstanceOf(InsufficientStockException.class);
        verify(comandaRepository, never()).save(any());
    }

    // ── Fechamento ───────────────────────────────────────────────────────────────────────────

    @Test
    void closeComanda_convertsAccumulatedItemsWithoutReQueryingTheCatalog() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), eq(new BigDecimal("25.00"))))
                .thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<PaymentCommand> payments = List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null));
        Order order = comandaService.closeComanda(10L, payments, "caixa1");

        assertThat(order.id()).isEqualTo(500L);
        assertThat(order.channel()).isEqualTo(SalesChannel.BALCAO);
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).unitPrice()).isEqualByComparingTo("25.00");
        // Nunca resolve o preço de novo pelo catálogo — o item já veio precificado da comanda.
        verifyNoInteractions(estoqueUseCase);
        verify(orderPaymentRepository).save(argThat(p -> p.orderId().equals(500L)
                && p.amount().compareTo(new BigDecimal("25.00")) == 0));
        verify(cashbackUseCase).recordEarnedForOrder(any());
        verify(comandaRepository).save(argThat(c -> c.status() == ComandaStatus.FECHADA
                && c.orderId().equals(500L)));
    }

    @Test
    void closeComanda_doesNotAdjustStockAgain() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(pdvService.validatePaymentsAndComputeChange(any(), any())).thenReturn(null);
        givenOrderPersistenceAssignsId();
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        comandaService.closeComanda(10L, List.of(new PaymentCommand(PaymentMethod.DINHEIRO,
                new BigDecimal("25.00"), null)), "caixa1");

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
    }

    @Test
    void closeComanda_refusesEmptyComandaBeforeTouchingOrders() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), "caixa1"))
                .isInstanceOf(ComandaEmptyException.class);
        verifyNoInteractions(orderRepository);
    }

    @Test
    void closeComanda_refusesNonAbertaComanda() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA,
                List.of(essenciaItem()), null, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.closeComanda(10L,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, BigDecimal.TEN, null)), "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verifyNoInteractions(orderRepository);
    }

    // ── Cancelamento ─────────────────────────────────────────────────────────────────────────

    @Test
    void cancelComanda_returnsStockPerItem() {
        Comanda comanda = abertaComanda(essenciaItem());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(comanda));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());
        when(comandaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Comanda cancelada = comandaService.cancelComanda(10L, "caixa1");

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        verify(estoqueUseCase).adjustStock(eq("ESS-MENTA"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(BigDecimal.ONE), eq("Cancelamento de comanda #10"), eq("caixa1"));
    }

    @Test
    void cancelComanda_refusesNonAbertaComanda() {
        Comanda fechada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.FECHADA,
                List.of(essenciaItem()), 500L, "caixa1", Instant.now(), Instant.now());
        when(comandaRepository.findById(10L)).thenReturn(Optional.of(fechada));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(openSession());

        assertThatThrownBy(() -> comandaService.cancelComanda(10L, "caixa1"))
                .isInstanceOf(ComandaNotOpenException.class);
        verifyNoInteractions(estoqueUseCase);
    }

    // ── Leitura ──────────────────────────────────────────────────────────────────────────────

    @Test
    void getComanda_throwsWhenNotFound() {
        when(comandaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> comandaService.getComanda(999L)).isInstanceOf(ComandaNotFoundException.class);
    }

    @Test
    void listOpenComandas_delegatesToRepository() {
        Comanda comanda = abertaComanda();
        when(comandaRepository.findOpenBySessionId(1L)).thenReturn(List.of(comanda));

        assertThat(comandaService.listOpenComandas(1L)).containsExactly(comanda);
    }
}
