package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public class OrderService implements OrderUseCase {

    private final OrderRepository orderRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final OrderPaymentRepository orderPaymentRepository;
    private final CashbackUseCase cashbackUseCase;

    public OrderService(OrderRepository orderRepository, EstoqueUseCase estoqueUseCase,
            OrderPaymentRepository orderPaymentRepository, CashbackUseCase cashbackUseCase) {
        this.orderRepository = orderRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.orderPaymentRepository = orderPaymentRepository;
        this.cashbackUseCase = cashbackUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listOrders(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size) {
        return orderRepository.findAll(channel, status, customerId, from, to, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional
    public Order changeStatus(Long orderId, OrderStatus newStatus, String username) {
        // A validação da transição mora em OrderStatus/Order e já tem teste — aqui só se orquestra.
        return orderRepository.save(getOrder(orderId).withStatus(newStatus));
    }

    @Override
    @Transactional
    public Order cancelOrder(Long orderId, String reason, String username) {
        Order order = getOrder(orderId);

        // cancelled() valida a transição (só sai de estado pré-pagamento) e recusa cancelar duas
        // vezes. Chamá-lo ANTES de mexer na reserva é o que impede um segundo cancelamento de
        // liberá-la de novo.
        Order cancelled = order.cancelled(reason, Instant.now());

        // Pré-pagamento nunca teve baixa real de estoque — só reserva (ou nem isso, no balcão, que
        // nunca reserva). Devolver com adjustStock(ENTRADA) aqui inflaria o físico com mercadoria
        // que nunca saiu; releaseReservationsByOwner já é idempotente/zero-safe por design.
        estoqueUseCase.releaseReservationsByOwner(Order.reservationOwnerReference(orderId), username);
        return orderRepository.save(cancelled);
    }

    @Override
    @Transactional
    public Order refundOrder(Long orderId, String reason, String username) {
        Order order = getOrder(orderId);

        // refunded() valida a transição (só sai de estado pós-pagamento) e recusa reembolsar duas
        // vezes. Chamá-lo ANTES de mexer em estoque/pagamento/cashback é o que impede um segundo
        // reembolso de estornar tudo de novo.
        Order refunded = order.refunded(reason, Instant.now());

        // Devolução é entrada de estoque legítima, inclusive para pedido já entregue. O motivo
        // carrega o número do pedido: sem ele, a trilha do movimento não é reconstruível.
        String movementReason = "Reembolso do pedido " + order.orderNumber();
        for (OrderItem item : order.items()) {
            estoqueUseCase.adjustStock(item.sku(), order.warehouseCode(), MovementType.ENTRADA,
                    item.quantity(), movementReason, username);
        }

        // Cada pagamento CAPTURED ganha uma linha REFUNDED do mesmo valor — nunca um update, pelo
        // mesmo motivo do resto do ledger. Pagamento que nunca chegou a ser capturado (FAILED,
        // PENDING) não tem o que estornar.
        for (OrderPayment payment : orderPaymentRepository.findByOrderId(orderId)) {
            if (payment.status() == PaymentStatus.CAPTURED) {
                orderPaymentRepository.save(OrderPayment.refunded(payment));
            }
        }

        // Reverte só os ganhos EARNED ainda não revertidos. Cashback resgatado (fatia futura de
        // resgate) não é desfeito por aqui.
        cashbackUseCase.reverseEarningsForOrder(order);

        return orderRepository.save(refunded);
    }
}
