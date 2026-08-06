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
        return withCountedItem(sku, countedQuantity, null);
    }

    /**
     * Mesmo que {@link #withCountedItem(String, BigDecimal)}, com lote explícito (EST-F008). A
     * chave do upsert passa a ser {@code (sku, lotCode)}: recontar o mesmo lote sobrescreve, mas
     * contar um lote diferente do mesmo SKU acrescenta uma linha nova — cada lote é uma contagem
     * própria, com seu próprio confronto contra {@code StockLot} no fechamento.
     */
    public StockCount withCountedItem(String sku, BigDecimal countedQuantity, String lotCode) {
        Map<String, StockCountItem> byKey = new LinkedHashMap<>();
        items.forEach(item -> byKey.put(itemKey(item.sku(), item.lotCode()), item));
        String key = itemKey(sku, lotCode);
        StockCountItem existing = byKey.get(key);
        byKey.put(key, existing == null
                ? StockCountItem.counted(sku, countedQuantity, lotCode)
                : new StockCountItem(existing.id(), sku, countedQuantity, null, null, lotCode));
        return new StockCount(id, warehouseId, status, username, createdAt, closedAt,
                new ArrayList<>(byKey.values()));
    }

    private static String itemKey(String sku, String lotCode) {
        return sku + "|" + (lotCode == null ? "" : lotCode);
    }

    /**
     * Confronta o item {@code (sku, lotCode)} contra {@code systemQuantity} — chamado logo depois
     * de {@link #withCountedItem}, no <b>registro</b> da contagem, não no fechamento (EST-C0xx).
     *
     * <p>Isso é o que permite ao fechamento aplicar a divergência encontrada sobre o saldo do
     * sistema NAQUELE INSTANTE (via {@code EstoqueService.closeAggregateSku}), em vez de
     * substituir o saldo pelo valor contado direto: entre o registro e o fechamento pode ter
     * havido movimentação legítima (uma venda, por exemplo), e essa movimentação não pode ser
     * apagada só porque o balanço fechou depois dela.</p>
     */
    public StockCount withReconciledItem(String sku, String lotCode, BigDecimal systemQuantity) {
        String key = itemKey(sku, lotCode);
        List<StockCountItem> updated = items.stream()
                .map(item -> itemKey(item.sku(), item.lotCode()).equals(key)
                        ? item.reconciledWith(systemQuantity)
                        : item)
                .toList();
        return new StockCount(id, warehouseId, status, username, createdAt, closedAt, updated);
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
