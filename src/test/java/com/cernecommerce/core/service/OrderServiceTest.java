package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock OrderRepository orderRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock CashbackUseCase cashbackUseCase;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, estoqueUseCase, orderPaymentRepository, cashbackUseCase);
    }

    private static List<OrderItem> twoCharcoals() {
        return List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")), null));
    }

    /** Venda de balcão já concluída — o caso mais comum de reembolso. */
    private static Order concludedBalcao() {
        return Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .concluded("000001000", null, NOW);
    }

    private static Order paidMarketplace() {
        return Order.openMarketplace(42L, "LOJA-01", twoCharcoals()).paid(NOW);
    }

    /** Pedido de marketplace ainda aguardando pagamento — o caso normal de cancelamento. */
    private static Order pendingMarketplace() {
        return Order.openMarketplace(42L, "LOJA-01", twoCharcoals());
    }

    // ── Leitura ──────────────────────────────────────────────────────────────────────────────

    @Test
    void getOrder_throwsWhenNotFound() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void listOrders_passesEveryFilterThrough() {
        Instant from = NOW.minusSeconds(3600);
        when(orderRepository.findAll(SalesChannel.MARKETPLACE, OrderStatus.PAGO, 42L, from, NOW, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        orderService.listOrders(SalesChannel.MARKETPLACE, OrderStatus.PAGO, 42L, from, NOW, 0, 20);

        verify(orderRepository).findAll(SalesChannel.MARKETPLACE, OrderStatus.PAGO, 42L, from, NOW, 0, 20);
    }

    @Test
    void listOrders_acceptsAllFiltersNull() {
        when(orderRepository.findAll(null, null, null, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        assertThat(orderService.listOrders(null, null, null, null, null, 0, 20).content()).isEmpty();
    }

    // ── Transição de estágio ─────────────────────────────────────────────────────────────────

    @Test
    void changeStatus_advancesThroughTheFulfillmentChain() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Order order = orderService.changeStatus(1L, OrderStatus.SEPARADO, "operador");

        assertThat(order.status()).isEqualTo(OrderStatus.SEPARADO);
    }

    @Test
    void changeStatus_refusesSkippingSteps() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.ENTREGUE, "operador"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(orderRepository, never()).save(any());
    }

    // ── Cancelamento pré-pagamento (libera reserva, nunca toca estoque real) ─────────────────

    @Test
    void cancelOrder_releasesTheReservationInsteadOfTouchingRealStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.releaseReservationsByOwner(any(), any())).thenReturn(1);

        Order cancelled = orderService.cancelOrder(1L, "cliente desistiu", "gerente");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELADO);
        assertThat(cancelled.cancelReason()).isEqualTo("cliente desistiu");
        assertThat(cancelled.cancelledAt()).isNotNull();
        verify(estoqueUseCase).releaseReservationsByOwner(eq(Order.reservationOwnerReference(1L)), eq("gerente"));
        // Nunca houve baixa real para devolver: só reserva.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelOrder_refusesToCancelTwiceAndDoesNotReleaseAgain() {
        Order alreadyCancelled = pendingMarketplace().cancelled("primeiro", NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(alreadyCancelled));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "segundo", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // O ponto do teste: um segundo cancelamento liberaria a reserva de novo.
        verify(estoqueUseCase, never()).releaseReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_propagatesReservationReleaseFailureAndDoesNotPersist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));
        when(estoqueUseCase.releaseReservationsByOwner(any(), any()))
                .thenThrow(new IllegalStateException("reserva não encontrada"));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "engano", "gerente"))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
    }

    /**
     * Cancelar e reembolsar são eventos diferentes (PDV-F007): pedido com pagamento confirmado
     * usa {@code refundOrder}, nunca {@code cancelOrder}.
     */
    @Test
    void cancelOrder_refusesOrdersThatAlreadyHavePaymentConfirmed() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(paidMarketplace()));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "engano", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(estoqueUseCase, never()).releaseReservationsByOwner(any(), any());
        verify(orderRepository, never()).save(any());
    }

    // ── Reembolso pós-pagamento: estoque (EST-F014), pagamento e cashback (PDV-F007) ─────────

    @Test
    void refundOrder_returnsTheGoodsToStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        Order refunded = orderService.refundOrder(1L, "cliente desistiu", "gerente");

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        assertThat(refunded.cancelReason()).isEqualTo("cliente desistiu");
        assertThat(refunded.refundedAt()).isNotNull();
        // ENTRADA, não SAIDA: devolução devolve mercadoria à prateleira.
        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), isNull(), isNull());
    }

    @Test
    void refundOrder_stampsTheOrderNumberInTheMovementReason() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "engano", "gerente");

        // Sem o número no motivo, a trilha do movimento não é reconstruível.
        verify(estoqueUseCase).adjustStock(any(), any(), any(), any(),
                eq("Reembolso do pedido 000001000"), any(), any(), any());
    }

    @Test
    void refundOrder_worksOnADeliveredOrderBecauseThatIsAReturn() {
        Order delivered = paidMarketplace()
                .withStatus(OrderStatus.SEPARADO)
                .withStatus(OrderStatus.ENVIADO)
                .withStatus(OrderStatus.ENTREGUE);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(delivered));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        Order refunded = orderService.refundOrder(1L, "devolução no prazo", "gerente");

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        verify(estoqueUseCase).adjustStock(any(), any(), eq(MovementType.ENTRADA), any(), any(), any(), any(), any());
    }

    @Test
    void refundOrder_comLoteInformado_propagaParaAdjustStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "devolução no prazo", "gerente",
                List.of(new OrderUseCase.RefundItemLot("CARV-001", "L1", LocalDate.parse("2027-01-01"))));

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), eq("L1"), eq(LocalDate.parse("2027-01-01")));
    }

    @Test
    void refundOrder_comLoteDeOutroSku_naoAfetaItemSemCorrespondencia() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        // Casamento é por sku: lote de um SKU que não está no pedido não deve vazar para CARV-001.
        orderService.refundOrder(1L, "devolução no prazo", "gerente",
                List.of(new OrderUseCase.RefundItemLot("ESSE-001", "L1", LocalDate.parse("2027-01-01"))));

        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"), isNull(), isNull());
    }

    @Test
    void refundOrder_refusesToRefundTwiceAndDoesNotTouchStock() {
        Order alreadyRefunded = concludedBalcao().refunded("primeiro", NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(alreadyRefunded));

        assertThatThrownBy(() -> orderService.refundOrder(1L, "segundo", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // O ponto do teste: um segundo reembolso devolveria a mercadoria de novo, inflando o saldo.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void refundOrder_propagatesStockFailureAndDoesNotPersist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("depósito inativo"));

        assertThatThrownBy(() -> orderService.refundOrder(1L, "engano", "gerente"))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void refundOrder_reversesEachCapturedPaymentWithMatchingMethodAndAmount() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        OrderPayment cash = OrderPayment.captured(1L, PaymentMethod.DINHEIRO, new BigDecimal("50.00"), null);
        OrderPayment debit = OrderPayment.captured(1L, PaymentMethod.DEBITO, new BigDecimal("30.00"), null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of(cash, debit));
        when(orderPaymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.refundOrder(1L, "devolução no prazo", "gerente");

        ArgumentCaptor<OrderPayment> captor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(OrderPayment::method, OrderPayment::amount, OrderPayment::status)
                .containsExactlyInAnyOrder(
                        tuple(PaymentMethod.DINHEIRO, new BigDecimal("50.00"), PaymentStatus.REFUNDED),
                        tuple(PaymentMethod.DEBITO, new BigDecimal("30.00"), PaymentStatus.REFUNDED));
    }

    @Test
    void refundOrder_skipsPaymentsThatAreNotCaptured() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        OrderPayment failed = OrderPayment.of(2L, 1L, PaymentMethod.DINHEIRO, new BigDecimal("10.00"),
                PaymentStatus.FAILED, null, null, Instant.now(), null, Instant.now());
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of(failed));

        orderService.refundOrder(1L, "engano", "gerente");

        verify(orderPaymentRepository, never()).save(any());
    }

    @Test
    void refundOrder_invokesCashbackReversalForTheOrder() {
        Order order = concludedBalcao();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of());

        orderService.refundOrder(1L, "devolução no prazo", "gerente");

        verify(cashbackUseCase).reverseEarningsForOrder(order);
    }

    /** Pré-pagamento nunca tem o que reembolsar — só {@code cancelOrder} faz sentido. */
    @Test
    void refundOrder_refusesOrdersThatHaveNotBeenPaidYet() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(pendingMarketplace()));

        assertThatThrownBy(() -> orderService.refundOrder(1L, "engano", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }
}
