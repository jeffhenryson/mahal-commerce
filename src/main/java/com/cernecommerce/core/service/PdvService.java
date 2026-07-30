package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PdvService implements PdvUseCase {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final CashRegisterRepository cashRegisterRepository;
    private final CashMovementRepository cashMovementRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final CashbackUseCase cashbackUseCase;

    /** Teto de desconto por pedido, em percentual sobre o bruto. */
    private final BigDecimal maxDiscountPercent;

    public PdvService(CashRegisterRepository cashRegisterRepository,
            CashMovementRepository cashMovementRepository, OrderRepository orderRepository,
            OrderPaymentRepository orderPaymentRepository, EstoqueUseCase estoqueUseCase,
            CashbackUseCase cashbackUseCase, BigDecimal maxDiscountPercent) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.cashbackUseCase = cashbackUseCase;
        this.maxDiscountPercent = maxDiscountPercent;
    }

    // ── Ciclo de caixa ───────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> listSessions(int page, int size) {
        return cashRegisterRepository.findAll(page, size);
    }

    @Override
    @Transactional
    public CashRegisterSession openSession(String operator, BigDecimal openingAmount, String warehouseCode) {
        // Um caixa por operador. O índice parcial único da V66 é quem garante isto sob concorrência;
        // esta checagem existe para devolver um erro legível em vez de uma violação de constraint.
        cashRegisterRepository.findOpenByOperator(operator).ifPresent(open -> {
            throw new CashRegisterSessionAlreadyOpenException(operator, open.id());
        });
        // Valida o depósito ANTES de carimbá-lo na sessão: descobrir que ele não existe na primeira
        // venda seria descobrir tarde demais, com o cliente no balcão.
        estoqueUseCase.getWarehouseByCode(warehouseCode);
        return cashRegisterRepository.save(
                CashRegisterSession.open(operator, openingAmount, warehouseCode));
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterSession getCurrentSession(String operator) {
        return cashRegisterRepository.findOpenByOperator(operator)
                .orElseThrow(() -> new NoOpenCashRegisterSessionException(operator));
    }

    @Override
    @Transactional(readOnly = true)
    public CashRegisterSession getSession(Long sessionId) {
        return cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
    }

    @Override
    @Transactional
    public CashMovement registerCashMovement(Long sessionId, CashMovementType type, BigDecimal amount,
            String reason, String username) {
        requireOwnOpenSession(sessionId, username);
        return cashMovementRepository.save(
                CashMovement.register(sessionId, type, amount, reason, username));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashMovement> listCashMovements(Long sessionId) {
        getSession(sessionId);
        return cashMovementRepository.findBySessionId(sessionId);
    }

    @Override
    @Transactional
    public CashRegisterSession closeSession(Long sessionId, BigDecimal countedAmount, String username) {
        CashRegisterSession session = getSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        // Fechar NÃO exige ser o dono: a conferência costuma ser do gerente, e é por isso que
        // PDV_SESSION_CLOSE existe separada de PDV_SESSION_MANAGE.
        //
        // PDV-F006: só DINHEIRO entra na conferência da gaveta. Débito, crédito e PIX não passam
        // pela mão do operador — eles se conferem contra o extrato da adquirente, não contra o
        // contado aqui. Somar tudo (como antes de order_payment existir) faria o fechamento
        // acusar sobra sempre que houvesse venda no cartão.
        BigDecimal expected = session.openingAmount()
                .add(orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(sessionId, PaymentMethod.DINHEIRO))
                .add(cashMovementRepository.sumSignedAmountBySessionId(sessionId));

        // Divergência não bloqueia — é o achado do fechamento, como no balanço de inventário.
        return cashRegisterRepository.save(session.closedWith(expected, countedAmount, username));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTotal> getSessionPaymentTotals(Long sessionId) {
        getSession(sessionId);
        List<PaymentTotal> totals = new ArrayList<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            totals.add(new PaymentTotal(method,
                    orderPaymentRepository.sumCapturedAmountBySessionIdAndMethod(sessionId, method)));
        }
        return totals;
    }

    // ── Venda ────────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Order registerSale(Long sessionId, Long customerId, List<SaleItemCommand> items,
            List<PaymentCommand> payments, String username) {
        CashRegisterSession session = requireOwnOpenSession(sessionId, username);

        // PDV-F004: o preço e o custo vêm do catálogo. findPricingBySku já lança
        // ProductNotFoundException para SKU inexistente, e fromCatalog recusa produto sem preço —
        // as duas checagens acontecem ANTES de qualquer escrita de estoque.
        List<OrderItem> orderItems = new ArrayList<>(items.size());
        for (SaleItemCommand command : items) {
            OrderItem item = OrderItem.fromCatalog(command.sku(), command.quantity(),
                    estoqueUseCase.findPricingBySku(command.sku()), command.discountAmount());
            // CRM-F003: a taxa é resolvida e carimbada aqui — mudar a taxa amanhã não pode
            // reescrever o cashback gerado por pedidos de ontem.
            CashbackRate resolvedRate = cashbackUseCase.resolveApplicableRate(command.sku());
            if (resolvedRate != null) {
                item = item.withCashbackPercent(resolvedRate.percent());
            }
            orderItems.add(item);
        }

        // PDV-C004: o depósito vem da SESSÃO, não do request. É o que impede o operador de baixar
        // estoque de um depósito que não é o do caixa dele.
        String warehouseCode = session.warehouseCode();
        Order order = Order.openBalcao(sessionId, warehouseCode, customerId, orderItems);
        requireDiscountWithinLimit(order);

        // PDV-F006: pagamento é validado ANTES de tocar o estoque — um pagamento insuficiente não
        // deveria custar um adjustStock que só vai ser desfeito pelo rollback da transação.
        BigDecimal changeAmount = validatePaymentsAndComputeChange(payments, order.netAmount());

        for (OrderItem item : order.items()) {
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.SAIDA, item.quantity(),
                    "Venda balcão sessão #" + sessionId, username);
        }

        // No balcão a mercadoria sai e o dinheiro entra no mesmo instante: CRIADO → CONCLUIDO na
        // mesma transação. A numeração é consumida aqui, na conclusão, e não na criação.
        Order saved = orderRepository.save(
                order.concluded(orderRepository.nextOrderNumber(), changeAmount, Instant.now()));

        for (PaymentCommand payment : payments) {
            orderPaymentRepository.save(OrderPayment.captured(saved.id(), payment.method(),
                    payment.amount(), payment.installments()));
        }
        cashbackUseCase.recordEarnedForOrder(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPayment> getOrderPayments(Long orderId) {
        getOrder(orderId);
        return orderPaymentRepository.findByOrderId(orderId);
    }

    /**
     * Valida a lista de pagamentos contra o líquido do pedido e devolve o troco.
     *
     * <p>Regra: a soma do que <b>não</b> é {@code DINHEIRO} não pode passar do líquido — débito,
     * crédito e PIX são lançados pelo valor exato que o operador decide cobrar, e só dinheiro pode
     * ser tendido a mais. Isso garante que todo excedente é explicável por dinheiro, e o troco é
     * simplesmente {@code total pago − líquido}.</p>
     */
    private BigDecimal validatePaymentsAndComputeChange(List<PaymentCommand> payments, BigDecimal netAmount) {
        BigDecimal nonCashTotal = BigDecimal.ZERO;
        BigDecimal cashTotal = BigDecimal.ZERO;
        for (PaymentCommand payment : payments) {
            if (payment.method() == PaymentMethod.DINHEIRO) {
                cashTotal = cashTotal.add(payment.amount());
            } else {
                nonCashTotal = nonCashTotal.add(payment.amount());
            }
        }
        if (nonCashTotal.compareTo(netAmount) > 0) {
            throw new PaymentExceedsOrderTotalException(nonCashTotal, netAmount);
        }
        BigDecimal totalPaid = cashTotal.add(nonCashTotal);
        if (totalPaid.compareTo(netAmount) < 0) {
            throw new InsufficientPaymentException(totalPaid, netAmount);
        }
        BigDecimal change = totalPaid.subtract(netAmount);
        return change.signum() > 0 ? change : null;
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listSessionOrders(Long sessionId, int page, int size) {
        getSession(sessionId);
        return orderRepository.findBySessionId(sessionId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listPendingOnlineOrders(int page, int size) {
        return orderRepository.findAll(SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO,
                null, null, null, page, size);
    }

    @Override
    @Transactional
    public Order settleOnlineOrder(Long sessionId, Long orderId, String username) {
        CashRegisterSession session = requireOwnOpenSession(sessionId, username);
        Order order = getOrder(orderId);

        // A reserva já segurou a mercadoria desde o checkout: consumi-la converte o reservado em
        // saída real. Chamar adjustStock(SAIDA) aqui debitaria o estoque duas vezes.
        estoqueUseCase.consumeReservationsByOwner(Order.reservationOwnerReference(orderId), username);

        // O canal continua MARKETPLACE — quem muda é o sessionId, que passa a dizer qual caixa
        // recebeu o dinheiro. concluded() valida a transição e recusa pedido que não está
        // aguardando pagamento.
        Order saved = orderRepository.save(order
                .withSession(session.id())
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));
        cashbackUseCase.recordEarnedForOrder(saved);
        return saved;
    }

    // ── Apoio ────────────────────────────────────────────────────────────────────────────────

    /**
     * Sessão aberta <b>e</b> do próprio operador. É a checagem que fecha o buraco de isolamento
     * documentado no README do módulo: antes, qualquer um com {@code PDV_SALE_MANAGE} vendia na
     * sessão de outro, e o fechamento daquele caixa acusava uma diferença sem dono.
     */
    private CashRegisterSession requireOwnOpenSession(Long sessionId, String username) {
        CashRegisterSession session = getSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        if (!session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
        return session;
    }

    private void requireDiscountWithinLimit(Order order) {
        if (order.discountAmount().signum() == 0 || order.grossAmount().signum() == 0) {
            return;
        }
        BigDecimal percent = order.discountAmount()
                .divide(order.grossAmount(), 6, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
        if (percent.compareTo(maxDiscountPercent) > 0) {
            throw new DiscountLimitExceededException(percent, maxDiscountPercent);
        }
    }
}
