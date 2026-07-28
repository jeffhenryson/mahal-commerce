package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
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
class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    @Mock OrderRepository orderRepository;
    @Mock EstoqueUseCase estoqueUseCase;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, estoqueUseCase);
    }

    private static List<OrderItem> twoCharcoals() {
        return List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")), null));
    }

    /** Venda de balcão já concluída — o caso mais comum de cancelamento. */
    private static Order concludedBalcao() {
        return Order.openBalcao(1L, "LOJA-01", null, twoCharcoals())
                .concluded("000001000", null, NOW);
    }

    private static Order paidMarketplace() {
        return Order.openMarketplace(42L, "LOJA-01", twoCharcoals()).paid(NOW);
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

    // ── Cancelamento com estorno de estoque (EST-F014) ───────────────────────────────────────

    @Test
    void cancelOrder_returnsTheGoodsToStock() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order cancelled = orderService.cancelOrder(1L, "cliente desistiu", "gerente");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELADO);
        assertThat(cancelled.cancelReason()).isEqualTo("cliente desistiu");
        assertThat(cancelled.cancelledAt()).isNotNull();
        // ENTRADA, não SAIDA: devolução devolve mercadoria à prateleira.
        verify(estoqueUseCase).adjustStock(eq("CARV-001"), eq("LOJA-01"), eq(MovementType.ENTRADA),
                eq(new BigDecimal("2.000")), any(), eq("gerente"));
    }

    @Test
    void cancelOrder_stampsTheOrderNumberInTheMovementReason() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        orderService.cancelOrder(1L, "engano", "gerente");

        // Sem o número no motivo, a trilha do movimento não é reconstruível.
        verify(estoqueUseCase).adjustStock(any(), any(), any(), any(),
                eq("Cancelamento do pedido 000001000"), any());
    }

    @Test
    void cancelOrder_refusesToCancelTwiceAndDoesNotTouchStock() {
        Order alreadyCancelled = concludedBalcao().cancelled("primeiro", NOW);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(alreadyCancelled));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "segundo", "gerente"))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        // O ponto do teste: um segundo cancelamento devolveria a mercadoria de novo, inflando o saldo.
        verify(estoqueUseCase, never()).adjustStock(any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_worksOnADeliveredOrderBecauseThatIsAReturn() {
        Order delivered = paidMarketplace()
                .withStatus(OrderStatus.SEPARADO)
                .withStatus(OrderStatus.ENVIADO)
                .withStatus(OrderStatus.ENTREGUE);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(delivered));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any())).thenReturn(null);

        Order cancelled = orderService.cancelOrder(1L, "devolução no prazo", "gerente");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELADO);
        verify(estoqueUseCase).adjustStock(any(), any(), eq(MovementType.ENTRADA), any(), any(), any());
    }

    @Test
    void cancelOrder_propagatesStockFailureAndDoesNotPersist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(concludedBalcao()));
        when(estoqueUseCase.adjustStock(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("depósito inativo"));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, "engano", "gerente"))
                .isInstanceOf(IllegalStateException.class);

        verify(orderRepository, never()).save(any());
    }
}
