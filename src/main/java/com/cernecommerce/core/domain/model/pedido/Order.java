package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Pedido de venda, de qualquer canal (PDV-F003).
 *
 * <p>Substitui a {@code Sale} anterior, que não tinha cliente, pagamento, desconto, status nem
 * cancelamento — não era "faltavam campos", era uma entidade que não representava um pedido.</p>
 *
 * <h2>Um canal, uma tabela</h2>
 * <p>Balcão e marketplace são o <b>mesmo</b> documento, distinguidos por {@link #channel}. Tudo que
 * vem depois — extrato do cliente, ledger de cashback, devolução, faturamento, documento fiscal,
 * relatório de margem — consulta "vendas, independente de canal". Com duas tabelas, cada um desses
 * consumidores pagaria um {@code UNION} ou duplicaria lógica.</p>
 *
 * <h2>O que deliberadamente não mora aqui</h2>
 * <p>Sessão de caixa, formas de pagamento, endereço e frete ficam em tabelas próprias, populadas só
 * pelo canal que as tem. É o que evita o pedido de 40 colunas em que metade é sempre nula.</p>
 *
 * <h2>{@code channel} é origem; {@code sessionId} é liquidação</h2>
 * <p>São dimensões independentes, e confundi-las custaria caro. {@link #channel} diz <b>onde o
 * pedido nasceu</b> e nunca muda. {@link #sessionId} diz <b>qual caixa o liquidou</b>.</p>
 *
 * <p>O caso que separa os dois: o cliente monta o pedido no aplicativo, vem à loja e paga no balcão.
 * Esse pedido continua sendo {@code MARKETPLACE} — foi o site que o gerou, e é assim que ele tem que
 * aparecer no relatório de conversão — mas o dinheiro entrou numa gaveta específica, e o fechamento
 * daquele caixa precisa contabilizá-lo. Reescrever o canal para {@code BALCAO} na liquidação faria o
 * marketplace parecer não vender nada.</p>
 *
 * <h2>Numeração</h2>
 * <p>{@link #orderNumber} vem de sequência própria e é emitido na <b>conclusão</b>, não na criação:
 * o {@code BIGSERIAL} do id deixa buracos quando uma transação faz rollback, e buraco em numeração
 * de documento fiscal é problema com o fisco.</p>
 */
public record Order(
        Long id,
        String orderNumber,
        SalesChannel channel,
        OrderStatus status,
        Long customerId,
        Long sessionId,
        String warehouseCode,
        List<OrderItem> items,
        BigDecimal grossAmount,
        BigDecimal discountAmount,
        BigDecimal cashbackRedeemed,
        BigDecimal netAmount,
        BigDecimal changeAmount,
        String cancelReason,
        Instant createdAt,
        Instant paidAt,
        Instant concludedAt,
        Instant cancelledAt,
        Instant refundedAt,
        long version) {

    public Order {
        if (channel == null) {
            throw new IllegalArgumentException("channel é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items não pode ser vazio");
        }
        items = List.copyOf(items);

        // Cliente: opcional no balcão (a venda anônima de passagem é a maioria), obrigatório no
        // marketplace — pedido online sem cliente não tem para quem entregar nem para quem estornar.
        if (channel == SalesChannel.MARKETPLACE && customerId == null) {
            throw new IllegalArgumentException("customerId é obrigatório em pedido de MARKETPLACE");
        }
        // Sessão de caixa: o balcão sempre tem uma. O marketplace normalmente não tem — mas PODE
        // ter, quando o cliente monta o pedido no app e vem pagar na loja. Ver a nota sobre
        // sessionId na documentação do tipo.
        if (channel == SalesChannel.BALCAO && sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório em venda de BALCAO");
        }

        grossAmount = requireNonNegative(grossAmount, "grossAmount");
        discountAmount = requireNonNegative(discountAmount, "discountAmount");
        cashbackRedeemed = requireNonNegative(cashbackRedeemed, "cashbackRedeemed");
        netAmount = requireNonNegative(netAmount, "netAmount");

        BigDecimal expectedNet = grossAmount.subtract(discountAmount).subtract(cashbackRedeemed);
        if (netAmount.compareTo(expectedNet) != 0) {
            throw new IllegalArgumentException("netAmount deve ser grossAmount - discountAmount - cashbackRedeemed: "
                    + "esperado " + expectedNet + ", recebido " + netAmount);
        }

        // Troco só existe onde há dinheiro em espécie mudando de mão.
        if (changeAmount != null) {
            if (changeAmount.signum() < 0) {
                throw new IllegalArgumentException("changeAmount não pode ser negativo");
            }
            if (channel != SalesChannel.BALCAO && changeAmount.signum() > 0) {
                throw new IllegalArgumentException("changeAmount só existe em venda de BALCAO");
            }
        }

        // Estado e carimbo de tempo não podem discordar: é a invariante que o CHECK do schema
        // espelha, para sobreviver a carga direta e script de correção.
        if ((status == OrderStatus.CANCELADO) != (cancelledAt != null)) {
            throw new IllegalArgumentException(
                    "status CANCELADO e cancelledAt têm que coexistir: status=" + status + ", cancelledAt=" + cancelledAt);
        }
        if ((status == OrderStatus.REEMBOLSADO) != (refundedAt != null)) {
            throw new IllegalArgumentException(
                    "status REEMBOLSADO e refundedAt têm que coexistir: status=" + status + ", refundedAt=" + refundedAt);
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal value, String field) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " não pode ser negativo");
        }
        return value;
    }

    /**
     * Abre uma venda de balcão em {@link OrderStatus#CRIADO}. Estado efêmero: quem chama conclui na
     * mesma transação, via {@link #concluded}.
     *
     * <p>O {@code warehouseCode} vem da <b>sessão</b>, não do chamador — é o que restringe a venda
     * ao depósito do caixa aberto e fecha o buraco de isolamento do módulo.</p>
     */
    public static Order openBalcao(Long sessionId, String warehouseCode, Long customerId, List<OrderItem> items) {
        Totals totals = Totals.from(items);
        return new Order(null, null, SalesChannel.BALCAO, OrderStatus.CRIADO, customerId, sessionId,
                warehouseCode, items, totals.gross(), totals.discount(), BigDecimal.ZERO, totals.net(),
                null, null, Instant.now(), null, null, null, null, 0L);
    }

    /**
     * Abre um pedido de marketplace em {@link OrderStatus#AGUARDANDO_PAGAMENTO} — o estado em que o
     * estoque já está reservado e a janela de pagamento está correndo.
     */
    public static Order openMarketplace(Long customerId, String warehouseCode, List<OrderItem> items) {
        Totals totals = Totals.from(items);
        return new Order(null, null, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO, customerId,
                null, warehouseCode, items, totals.gross(), totals.discount(), BigDecimal.ZERO, totals.net(),
                null, null, Instant.now(), null, null, null, null, 0L);
    }

    /** Reconstitui um pedido a partir de persistência. */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, Instant refundedAt, long version) {
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, version);
    }

    /**
     * Conclui a venda: carimba o número do pedido e o instante da conclusão.
     *
     * @param orderNumber numeração emitida <b>agora</b>, por sequência própria — ver a nota de
     *        numeração na documentação do tipo
     * @param changeAmount troco devolvido, ou {@code null} quando não houve dinheiro em espécie
     */
    public Order concluded(String orderNumber, BigDecimal changeAmount, Instant concludedAt) {
        requireTransition(OrderStatus.CONCLUIDO);
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException("orderNumber é obrigatório na conclusão do pedido");
        }
        return new Order(id, orderNumber, channel, OrderStatus.CONCLUIDO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, cancelReason, createdAt, paidAt == null ? concludedAt : paidAt,
                concludedAt, null, null, version);
    }

    /** Marca o pagamento como confirmado — caminho do marketplace, disparado pelo webhook. */
    public Order paid(Instant paidAt) {
        requireTransition(OrderStatus.PAGO);
        return new Order(id, orderNumber, channel, OrderStatus.PAGO, customerId, sessionId, warehouseCode,
                items, grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, null, null, version);
    }

    /** Avança o pedido na esteira de fulfillment ({@code SEPARADO → ENVIADO → ENTREGUE}). */
    public Order withStatus(OrderStatus newStatus) {
        requireTransition(newStatus);
        return new Order(id, orderNumber, channel, newStatus, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, null, null, version);
    }

    /**
     * Cancela o pedido ANTES de qualquer pagamento confirmado — nunca houve dinheiro capturado
     * nem baixa real de estoque para desfazer. A liberação da reserva é responsabilidade do
     * service. Pedido com pagamento confirmado usa {@link #refunded} — cancelar e reembolsar são
     * eventos diferentes (PDV-F007).
     */
    public Order cancelled(String reason, Instant cancelledAt) {
        requireTransition(OrderStatus.CANCELADO);
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelledAt é obrigatório no cancelamento");
        }
        return new Order(id, orderNumber, channel, OrderStatus.CANCELADO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, reason, createdAt, paidAt, concludedAt, cancelledAt, null, version);
    }

    /**
     * Reembolsa o pedido DEPOIS de pagamento confirmado. Os estornos de estoque, pagamento e
     * cashback são responsabilidade do service — aqui só se valida a transição e se carimba o
     * motivo e o instante.
     */
    public Order refunded(String reason, Instant refundedAt) {
        requireTransition(OrderStatus.REEMBOLSADO);
        if (refundedAt == null) {
            throw new IllegalArgumentException("refundedAt é obrigatório no reembolso");
        }
        return new Order(id, orderNumber, channel, OrderStatus.REEMBOLSADO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, reason, createdAt, paidAt, concludedAt, null, refundedAt, version);
    }

    /**
     * Registra o resgate de cashback, que abate do líquido a pagar.
     *
     * <p>Resgate <b>não</b> é forma de pagamento: é um desconto no pedido mais uma entrada no ledger
     * de pontos. Misturar com o pagamento faria o DRE contar a mesma receita duas vezes.</p>
     */
    public Order withCashbackRedeemed(BigDecimal redeemed) {
        BigDecimal value = redeemed == null ? BigDecimal.ZERO : redeemed;
        BigDecimal newNet = grossAmount.subtract(discountAmount).subtract(value);
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, value, newNet, changeAmount, cancelReason, createdAt,
                paidAt, concludedAt, cancelledAt, refundedAt, version);
    }

    /**
     * Identificador que a reserva de estoque usa para apontar de volta para este pedido.
     *
     * <p>O formato mora aqui, e não espalhado por quem reserva e por quem consome, porque
     * {@code stock_reservation.owner_reference} é texto livre — não há FK que force os dois lados a
     * concordarem. O {@code COMMENT} da coluna na V64 já antecipava este formato.</p>
     */
    public static String reservationOwnerReference(Long orderId) {
        return "ORDER:" + orderId;
    }

    /**
     * Vincula o pedido à sessão de caixa que o liquidou. É o que acontece quando um pedido montado
     * no aplicativo é pago no balcão: o canal continua {@code MARKETPLACE}, mas o dinheiro entrou
     * numa gaveta específica e o fechamento dela precisa contabilizá-lo.
     */
    public Order withSession(Long newSessionId) {
        return new Order(id, orderNumber, channel, status, customerId, newSessionId, warehouseCode,
                items, grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount,
                cancelReason, createdAt, paidAt, concludedAt, cancelledAt, refundedAt, version);
    }

    /** Vincula o pedido a um cliente identificado depois da montagem — o "CPF na nota?" do balcão. */
    public Order withCustomer(Long newCustomerId) {
        return new Order(id, orderNumber, channel, status, newCustomerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, refundedAt, version);
    }

    /** Soma do cashback gerado por todos os itens; ignora itens sem taxa carimbada. */
    public BigDecimal totalCashbackEarned() {
        return items.stream()
                .map(OrderItem::cashbackAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Indica se o pedido ainda pode mudar de estado. */
    public boolean isCancelled() {
        return status == OrderStatus.CANCELADO;
    }

    private void requireTransition(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidOrderStatusTransitionException(id, status, target);
        }
    }

    /** Totais derivados dos itens. O servidor calcula; o cliente HTTP nunca informa. */
    private record Totals(BigDecimal gross, BigDecimal discount, BigDecimal net) {

        static Totals from(List<OrderItem> items) {
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("items não pode ser vazio");
            }
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal discount = BigDecimal.ZERO;
            for (OrderItem item : items) {
                BigDecimal itemGross = item.grossAmount();
                if (itemGross == null) {
                    throw new IllegalArgumentException(
                            "item sem preço congelado não pode compor pedido novo: " + item.sku());
                }
                gross = gross.add(itemGross);
                discount = discount.add(item.discountAmount());
            }
            return new Totals(gross, discount, gross.subtract(discount));
        }
    }
}
