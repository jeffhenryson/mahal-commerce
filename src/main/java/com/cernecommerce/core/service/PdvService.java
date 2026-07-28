package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
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
    private final OrderRepository orderRepository;
    private final EstoqueUseCase estoqueUseCase;

    /** Teto de desconto por pedido, em percentual sobre o bruto. */
    private final BigDecimal maxDiscountPercent;

    public PdvService(CashRegisterRepository cashRegisterRepository, OrderRepository orderRepository,
            EstoqueUseCase estoqueUseCase, BigDecimal maxDiscountPercent) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.orderRepository = orderRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.maxDiscountPercent = maxDiscountPercent;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CashRegisterSession> listSessions(int page, int size) {
        return cashRegisterRepository.findAll(page, size);
    }

    @Override
    @Transactional
    public Order registerSale(Long sessionId, String warehouseCode, Long customerId,
            List<SaleItemCommand> items, String username) {
        CashRegisterSession session = cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
        if (session.status() != CashRegisterSession.Status.OPEN) {
            throw new CashRegisterSessionClosedException(sessionId);
        }

        // PDV-F004: o preço e o custo vêm do catálogo. findPricingBySku já lança
        // ProductNotFoundException para SKU inexistente, e fromCatalog recusa produto sem preço —
        // as duas checagens acontecem ANTES de qualquer escrita de estoque.
        List<OrderItem> orderItems = new ArrayList<>(items.size());
        for (SaleItemCommand command : items) {
            orderItems.add(OrderItem.fromCatalog(command.sku(), command.quantity(),
                    estoqueUseCase.findPricingBySku(command.sku()), command.discountAmount()));
        }

        // O depósito ainda vem do request. Passa a vir da SESSÃO em PDV-C004 (Fatia 1), quando
        // cash_register_session ganhar a coluna warehouse_code — hoje a sessão não a tem, e
        // inventá-la aqui seria antecipar a migration do ciclo de caixa.
        Order order = Order.openBalcao(sessionId, warehouseCode, customerId, orderItems);
        requireDiscountWithinLimit(order);

        for (OrderItem item : order.items()) {
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.SAIDA, item.quantity(),
                    "Venda balcão sessão #" + sessionId, username);
        }

        // No balcão a mercadoria sai e o dinheiro entra no mesmo instante: CRIADO → CONCLUIDO na
        // mesma transação. A numeração é consumida aqui, na conclusão, e não na criação.
        Instant now = Instant.now();
        return orderRepository.save(order.concluded(orderRepository.nextOrderNumber(), null, now));
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listSessionOrders(Long sessionId, int page, int size) {
        if (cashRegisterRepository.findById(sessionId).isEmpty()) {
            throw new CashRegisterSessionNotFoundException(sessionId);
        }
        return orderRepository.findBySessionId(sessionId, page, size);
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
