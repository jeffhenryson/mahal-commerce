package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Balanço de inventário de um depósito (EST-F006): a sessão em que se conta a prateleira, se
 * confronta com o saldo do sistema e se aplicam os ajustes de uma vez.
 *
 * <p>A contagem existe como entidade — em vez de um ajuste avulso por SKU — porque o balanço é o
 * evento que a operação reconhece: dá para contar aos poucos, conferir a divergência antes de
 * mexer no saldo, e depois auditar quem contou o quê e quanto faltava.</p>
 *
 * <p>É por depósito, e não global: {@link StockBalance} é por par SKU/depósito, então contar
 * "o estoque" sem dizer onde não teria significado.</p>
 */
public record StockCount(Long id, Long warehouseId, StockCountStatus status, String username,
        Instant createdAt, Instant closedAt, List<StockCountItem> items) {

    public StockCount {
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Abre um balanço vazio para o depósito. */
    public static StockCount open(Long warehouseId, String username) {
        return new StockCount(null, warehouseId, StockCountStatus.ABERTA, username,
                Instant.now(), null, List.of());
    }

    /** Reconstitui a partir de persistência. */
    public static StockCount of(Long id, Long warehouseId, StockCountStatus status, String username,
            Instant createdAt, Instant closedAt, List<StockCountItem> items) {
        return new StockCount(id, warehouseId, status, username, createdAt, closedAt, items);
    }

    public boolean isOpen() {
        return status == StockCountStatus.ABERTA;
    }

    /**
     * Registra a contagem de um SKU. É <b>upsert</b>: recontar o mesmo SKU sobrescreve o valor
     * anterior em vez de acrescentar uma segunda linha — recontagem é o caso normal num balanço,
     * e duas linhas para o mesmo SKU tornariam o fechamento ambíguo.
     *
     * <p>A posição original do SKU na lista é preservada, para a ordem de exibição não pular
     * quando um item é recontado.</p>
     */
    public StockCount withCountedItem(String sku, BigDecimal countedQuantity) {
        Map<String, StockCountItem> bySku = new LinkedHashMap<>();
        items.forEach(item -> bySku.put(item.sku(), item));
        StockCountItem existing = bySku.get(sku);
        bySku.put(sku, existing == null
                ? StockCountItem.counted(sku, countedQuantity)
                : new StockCountItem(existing.id(), sku, countedQuantity, null, null));
        return new StockCount(id, warehouseId, status, username, createdAt, closedAt,
                new ArrayList<>(bySku.values()));
    }

    /** Substitui os itens pelos já confrontados com o saldo do sistema. */
    public StockCount withReconciledItems(List<StockCountItem> reconciled) {
        return new StockCount(id, warehouseId, status, username, createdAt, closedAt, reconciled);
    }

    /** Marca como fechada, carimbando o instante. Os ajustes de saldo são aplicados pelo service. */
    public StockCount closed() {
        return new StockCount(id, warehouseId, StockCountStatus.FECHADA, username, createdAt,
                Instant.now(), items);
    }

    /** Marca como cancelada. Não toca em saldo nenhum. */
    public StockCount cancelled() {
        return new StockCount(id, warehouseId, StockCountStatus.CANCELADA, username, createdAt,
                Instant.now(), items);
    }
}
