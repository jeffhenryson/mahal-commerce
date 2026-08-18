package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Comanda de mesa (PDV-F009): pedidos incrementais acumulados numa sessão de caixa aberta por
 * horas — o caso do lounge de narguilé, distinto da venda pontual de balcão que {@code Order} já
 * cobre. {@code ABERTA} recebe itens um a um (cada um já debita estoque na hora, ver
 * {@code ComandaService.addItem}); fecha virando um {@code Order} de verdade
 * ({@link ComandaStatus#FECHADA}) ou é abandonada sem cobrança ({@link ComandaStatus#CANCELADA}).
 *
 * <h2>Por que não é um {@code Order} desde o início</h2>
 * <p>{@code Order.items} é uma lista fechada no nascimento do pedido — não há como acrescentar item
 * depois. A comanda precisa exatamente do oposto: crescer aos poucos, por horas, antes de o pedido
 * existir de verdade. Ela só vira {@code Order} no fechamento, quando os itens acumulados são
 * convertidos de uma vez (ver {@code ComandaService.closeComanda}).</p>
 *
 * @param warehouseCode depósito da sessão de caixa que abriu a comanda — mesma regra de
 *        {@code CashRegisterSession.warehouseCode} (PDV-C004): o operador não baixa estoque de
 *        depósito alheio pela porta da comanda.
 * @param orderId preenchido só no fechamento. Nulo em {@code ABERTA} e em {@code CANCELADA} —
 *        comanda cancelada nunca vira pedido.
 */
public record Comanda(
        Long id,
        Long sessionId,
        String warehouseCode,
        String tableOrCustomerLabel,
        ComandaStatus status,
        List<ComandaItem> items,
        Long orderId,
        String openedBy,
        Instant openedAt,
        Instant closedAt) {

    public Comanda {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (tableOrCustomerLabel == null || tableOrCustomerLabel.isBlank()) {
            throw new IllegalArgumentException("tableOrCustomerLabel é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        items = items == null ? List.of() : List.copyOf(items);
        if (openedBy == null || openedBy.isBlank()) {
            throw new IllegalArgumentException("openedBy é obrigatório");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt é obrigatório");
        }
        // Espelha o CHECK ck_comanda_status_consistency da V104.
        switch (status) {
            case ABERTA -> {
                if (closedAt != null || orderId != null) {
                    throw new IllegalArgumentException(
                            "comanda ABERTA não pode ter closedAt nem orderId: closedAt=" + closedAt
                                    + ", orderId=" + orderId);
                }
            }
            case FECHADA -> {
                if (closedAt == null || orderId == null) {
                    throw new IllegalArgumentException(
                            "comanda FECHADA exige closedAt e orderId: closedAt=" + closedAt
                                    + ", orderId=" + orderId);
                }
            }
            case CANCELADA -> {
                if (closedAt == null) {
                    throw new IllegalArgumentException("comanda CANCELADA exige closedAt");
                }
                if (orderId != null) {
                    throw new IllegalArgumentException(
                            "comanda CANCELADA não pode ter orderId — nunca virou pedido: orderId=" + orderId);
                }
            }
        }
    }

    /** Abre uma comanda nova, vazia, na sessão informada. */
    public static Comanda open(Long sessionId, String warehouseCode, String tableOrCustomerLabel, String openedBy) {
        return new Comanda(null, sessionId, warehouseCode, tableOrCustomerLabel, ComandaStatus.ABERTA,
                List.of(), null, openedBy, Instant.now(), null);
    }

    /** Reconstitui uma comanda a partir de persistência. */
    public static Comanda of(Long id, Long sessionId, String warehouseCode, String tableOrCustomerLabel,
            ComandaStatus status, List<ComandaItem> items, Long orderId, String openedBy, Instant openedAt,
            Instant closedAt) {
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, status, items, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * Acrescenta um item à comanda aberta. Cópia — a comanda permanece imutável.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}. O service checa isso
     *         antes, com um erro tipado (409) — esta é a rede de segurança do domínio, mesmo
     *         padrão de {@code CashRegisterSession.closedWith}.
     */
    public Comanda withAddedItem(ComandaItem item) {
        requireOpen();
        List<ComandaItem> newItems = new ArrayList<>(items);
        newItems.add(item);
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, status, newItems, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * Fecha a comanda, vinculando o {@code Order} gerado a partir dos itens acumulados.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     */
    public Comanda closed(Long orderId, Instant closedAt) {
        requireOpen();
        if (orderId == null) {
            throw new IllegalArgumentException("orderId é obrigatório ao fechar a comanda");
        }
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt é obrigatório ao fechar a comanda");
        }
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, ComandaStatus.FECHADA,
                items, orderId, openedBy, openedAt, closedAt);
    }

    /**
     * Abandona a comanda sem cobrança — nunca vira pedido. O estorno do estoque já debitado item a
     * item é responsabilidade do service, mesma divisão de trabalho de {@code Order.cancelled}.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     */
    public Comanda cancelled(Instant closedAt) {
        requireOpen();
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt é obrigatório ao cancelar a comanda");
        }
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, ComandaStatus.CANCELADA,
                items, null, openedBy, openedAt, closedAt);
    }

    /** Soma dos subtotais dos itens já lançados. */
    public BigDecimal runningTotal() {
        return items.stream().map(ComandaItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isOpen() {
        return status == ComandaStatus.ABERTA;
    }

    private void requireOpen() {
        if (status != ComandaStatus.ABERTA) {
            throw new IllegalStateException("comanda " + id + " não está aberta: " + status);
        }
    }
}
