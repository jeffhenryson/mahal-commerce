package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>Casos de uso previstos (PDV-F001/F002): {@code openSession}, {@code registerCashMovement}
 * (sangria/suprimento) e {@code closeSession} com conferência.</p>
 */
public interface PdvUseCase {

    /** Lista as sessões de caixa paginadas. */
    PageResult<CashRegisterSession> listSessions(int page, int size);

    /**
     * Registra uma venda de balcão e dá baixa no estoque, tudo na mesma transação (PDV-F003/F004).
     *
     * <p>O preço e o custo de cada item vêm do <b>catálogo</b>, nunca do chamador: o request informa
     * SKU, quantidade e, opcionalmente, desconto. Saldo insuficiente em qualquer item reverte a
     * venda inteira.</p>
     *
     * @param sessionId sessão de caixa aberta
     * @param warehouseCode depósito de onde sai a mercadoria. Passa a vir da <b>sessão</b> em
     *        PDV-C004 (Fatia 1), quando {@code cash_register_session} ganhar a coluna
     * @param customerId cliente identificado, ou {@code null} — a venda anônima de passagem é o
     *        caso normal do balcão
     * @param items o que vender: SKU, quantidade e desconto por item
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException
     *         se a sessão não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException
     *         se a sessão já estiver fechada
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException
     *         se algum item não tiver preço no catálogo
     * @throws com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException
     *         se o desconto total passar do teto configurado
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException
     *         se o saldo de algum item for insuficiente
     */
    Order registerSale(Long sessionId, String warehouseCode, Long customerId,
            List<SaleItemCommand> items, String username);

    /**
     * Busca um pedido pelo id (PDV-F005).
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se não existir
     */
    Order getOrder(Long orderId);

    /** Pedidos de uma sessão de caixa, do mais recente para o mais antigo (PDV-F005). */
    PageResult<Order> listSessionOrders(Long sessionId, int page, int size);

    /**
     * O que o chamador informa por item de venda: <b>não</b> inclui preço.
     *
     * <p>É a diferença central de PDV-F004. Antes, {@code SaleItemRequest.unitPrice} era digitado
     * pelo cliente HTTP, e quem tivesse {@code PDV_SALE_MANAGE} vendia qualquer coisa por qualquer
     * valor sem deixar trilha de desconto.</p>
     */
    record SaleItemCommand(String sku, BigDecimal quantity, BigDecimal discountAmount) {
    }
}
