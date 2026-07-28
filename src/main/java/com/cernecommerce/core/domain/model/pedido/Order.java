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
        // Sessão de caixa: exatamente o contrário. O balcão sempre tem uma; o marketplace nunca.
        if (channel == SalesChannel.BALCAO && sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório em venda de BALCAO");
        }
        if (channel == SalesChannel.MARKETPLACE && sessionId != null) {
            throw new IllegalArgumentException("pedido de MARKETPLACE não tem sessão de caixa");
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
                null, null, Instant.now(), null, null, null, 0L);
    }

    /**
     * Abre um pedido de marketplace em {@link OrderStatus#AGUARDANDO_PAGAMENTO} — o estado em que o
     * estoque já está reservado e a janela de pagamento está correndo.
     */
    public static Order openMarketplace(Long customerId, String warehouseCode, List<OrderItem> items) {
        Totals totals = Totals.from(items);
        return new Order(null, null, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO, customerId,
                null, warehouseCode, items, totals.gross(), totals.discount(), BigDecimal.ZERO, totals.net(),
                null, null, Instant.now(), null, null, null, 0L);
    }

    /** Reconstitui um pedido a partir de persistência. */
    public static Order of(Long id, String orderNumber, SalesChannel channel, OrderStatus status,
            Long customerId, Long sessionId, String warehouseCode, List<OrderItem> items,
            BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal cashbackRedeemed,
            BigDecimal netAmount, BigDecimal changeAmount, String cancelReason, Instant createdAt,
            Instant paidAt, Instant concludedAt, Instant cancelledAt, long version) {
        return new Order(id, orderNumber, channel, status, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, version);
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
                concludedAt, null, version);
    }

    /** Marca o pagamento como confirmado — caminho do marketplace, disparado pelo webhook. */
    public Order paid(Instant paidAt) {
        requireTransition(OrderStatus.PAGO);
        return new Order(id, orderNumber, channel, OrderStatus.PAGO, customerId, sessionId, warehouseCode,
                items, grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, null, version);
    }

    /** Avança o pedido na esteira de fulfillment ({@code SEPARADO → ENVIADO → ENTREGUE}). */
    public Order withStatus(OrderStatus newStatus) {
        requireTransition(newStatus);
        return new Order(id, orderNumber, channel, newStatus, customerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, null, version);
    }

    /** Cancela o pedido. Os estornos de estoque, cashback e pagamento são responsabilidade do service. */
    public Order cancelled(String reason, Instant cancelledAt) {
        requireTransition(OrderStatus.CANCELADO);
        if (cancelledAt == null) {
            throw new IllegalArgumentException("cancelledAt é obrigatório no cancelamento");
        }
        return new Order(id, orderNumber, channel, OrderStatus.CANCELADO, customerId, sessionId,
                warehouseCode, items, grossAmount, discountAmount, cashbackRedeemed, netAmount,
                changeAmount, reason, createdAt, paidAt, concludedAt, cancelledAt, version);
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
                paidAt, concludedAt, cancelledAt, version);
    }

    /** Vincula o pedido a um cliente identificado depois da montagem — o "CPF na nota?" do balcão. */
    public Order withCustomer(Long newCustomerId) {
        return new Order(id, orderNumber, channel, status, newCustomerId, sessionId, warehouseCode, items,
                grossAmount, discountAmount, cashbackRedeemed, netAmount, changeAmount, cancelReason,
                createdAt, paidAt, concludedAt, cancelledAt, version);
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
