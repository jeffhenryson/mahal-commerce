package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>O ciclo de caixa (PDV-F001/F002) espelha o balanço de inventário: o fechamento confronta
 * contado × esperado, carimba a divergência e <b>fecha mesmo assim</b>.</p>
 */
public interface PdvUseCase {

    // ── Ciclo de caixa ───────────────────────────────────────────────────────────────────────

    /** Lista as sessões de caixa paginadas. */
    PageResult<CashRegisterSession> listSessions(int page, int size);

    /**
     * Abre um caixa para o operador.
     *
     * @param warehouseCode depósito de onde sairá a mercadoria vendida neste caixa. Fica na sessão
     *        para o operador não baixar estoque de depósito alheio (PDV-C004)
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException
     *         se o operador já tiver um caixa aberto
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException
     *         se o depósito não existir
     */
    CashRegisterSession openSession(String operator, BigDecimal openingAmount, String warehouseCode);

    /**
     * Caixa aberto do operador.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException
     *         se não houver nenhum
     */
    CashRegisterSession getCurrentSession(String operator);

    /**
     * Busca uma sessão pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException
     *         se não existir
     */
    CashRegisterSession getSession(Long sessionId);

    /**
     * Registra sangria ou suprimento. Exige sessão aberta <b>e do próprio operador</b>.
     *
     * @param amount sempre positivo — o sentido vem de {@code type}
     */
    CashMovement registerCashMovement(Long sessionId, CashMovementType type, BigDecimal amount,
            String reason, String username);

    /** Movimentos de uma sessão, na ordem em que aconteceram. */
    List<CashMovement> listCashMovements(Long sessionId);

    /**
     * Fecha o caixa confrontando o contado com o esperado.
     *
     * <p><b>Divergência não bloqueia</b> — é registrada, exatamente como no fechamento de um balanço
     * de inventário. Recusar o fechamento só produziria caixas que nunca fecham.</p>
     *
     * <p>Ao contrário das demais operações da sessão, fechar <b>não</b> exige ser o dono: a
     * conferência é frequentemente feita pelo gerente, e é por isso que existe a permissão
     * {@code PDV_SESSION_CLOSE} separada de {@code PDV_SESSION_MANAGE}.</p>
     */
    CashRegisterSession closeSession(Long sessionId, BigDecimal countedAmount, String username);

    /**
     * Totais recebidos na sessão, agrupados por forma de pagamento — só pagamento {@code CAPTURED}
     * conta. É o detalhamento que acompanha o fechamento: <b>só {@code DINHEIRO} entra na
     * conferência da gaveta</b>; débito, crédito e PIX vão para conferência contra a adquirente,
     * não contra o contado no caixa.
     *
     * <p>Devolve as quatro formas sempre, mesmo com total zero — forma que a sessão nunca usou não
     * desaparece da lista, só aparece zerada.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException
     *         se a sessão não existir
     */
    List<PaymentTotal> getSessionPaymentTotals(Long sessionId);

    // ── Venda ────────────────────────────────────────────────────────────────────────────────

    /**
     * Registra uma venda de balcão, captura o(s) pagamento(s) e dá baixa no estoque, tudo na mesma
     * transação (PDV-F003/F004/F006).
     *
     * <p>O preço e o custo de cada item vêm do <b>catálogo</b>, nunca do chamador. O depósito vem da
     * <b>sessão</b>, nunca do request. Saldo insuficiente em qualquer item reverte a venda inteira.</p>
     *
     * <p>Pagamento é validado <b>antes</b> de tocar o estoque: {@code payments} tem que somar pelo
     * menos o líquido do pedido, e a parte que não é {@code DINHEIRO} não pode sozinha passar do
     * líquido — só dinheiro pode ser tendido a mais para virar troco. O troco entra no pedido
     * concluído, nunca como uma linha de pagamento.</p>
     *
     * @param customerId cliente identificado, ou {@code null} — a venda anônima de passagem é o caso
     *        normal do balcão
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está vendendo
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException
     *         se algum item não tiver preço no catálogo
     * @throws com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException
     *         se o desconto total passar do teto configurado
     * @throws com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException
     *         se a soma dos pagamentos não cobrir o líquido do pedido
     * @throws com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException
     *         se a soma dos pagamentos que não são {@code DINHEIRO} passar do líquido do pedido
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException
     *         se o saldo de algum item for insuficiente
     */
    Order registerSale(Long sessionId, Long customerId, List<SaleItemCommand> items,
            List<PaymentCommand> payments, String username);

    /** Pagamentos de um pedido, na ordem em que foram lançados (PDV-F006). */
    List<OrderPayment> getOrderPayments(Long orderId);

    /**
     * Busca um pedido pelo id (PDV-F005).
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se não existir
     */
    Order getOrder(Long orderId);

    /** Pedidos de uma sessão de caixa, do mais recente para o mais antigo (PDV-F005). */
    PageResult<Order> listSessionOrders(Long sessionId, int page, int size);

    /**
     * Pedidos feitos no aplicativo que ainda aguardam pagamento — a lista que o caixa consulta
     * quando o cliente chega à loja para retirar e pagar.
     */
    PageResult<Order> listPendingOnlineOrders(int page, int size);

    /**
     * Liquida no balcão um pedido montado no aplicativo: recebe o pagamento, entrega a mercadoria e
     * conclui.
     *
     * <p>O canal <b>continua</b> {@code MARKETPLACE} — foi o site que gerou a venda, e é assim que
     * ela tem que aparecer no relatório de conversão. O que muda é o {@code sessionId}, que passa a
     * apontar para o caixa que recebeu o dinheiro, de modo que o fechamento daquela gaveta o
     * contabilize.</p>
     *
     * <p>O estoque desse pedido <b>já está reservado</b> desde o checkout, então a liquidação
     * <b>consome a reserva</b> em vez de dar baixa nova — dar baixa aqui debitaria a mercadoria duas
     * vezes.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se o pedido não
     *         existir
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido não estiver aguardando pagamento
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está liquidando
     */
    Order settleOnlineOrder(Long sessionId, Long orderId, String username);

    /**
     * O que o chamador informa por item de venda: <b>não</b> inclui preço.
     *
     * <p>É a diferença central de PDV-F004. Antes, {@code SaleItemRequest.unitPrice} era digitado
     * pelo cliente HTTP, e quem tivesse {@code PDV_SALE_MANAGE} vendia qualquer coisa por qualquer
     * valor sem deixar trilha de desconto.</p>
     */
    record SaleItemCommand(String sku, BigDecimal quantity, BigDecimal discountAmount) {
    }

    /**
     * O que o chamador informa por linha de pagamento (PDV-F006).
     *
     * @param installments só faz sentido com {@code method == CREDITO}; {@code null} nos demais
     */
    record PaymentCommand(PaymentMethod method, BigDecimal amount, Integer installments) {
    }

    /** Total recebido por forma de pagamento numa sessão — só {@code CAPTURED} conta. */
    record PaymentTotal(PaymentMethod method, BigDecimal amount) {
    }
}
