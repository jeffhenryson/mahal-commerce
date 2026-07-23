package com.cernecommerce.core.domain.model.compras;

import java.time.Instant;
import java.util.List;

/**
 * Recebimento de mercadoria de um {@link Supplier}, com entrada automática correspondente
 * no estoque (uma {@code StockMovement} de ENTRADA por item, via
 * {@code EstoqueUseCase.adjustStock}).
 */
public record GoodsReceipt(Long id, Long supplierId, String warehouseCode, List<GoodsReceiptItem> items,
        String username, Instant receivedAt) {

    public GoodsReceipt {
        if (supplierId == null) {
            throw new IllegalArgumentException("supplierId é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items não pode ser vazio");
        }
        items = List.copyOf(items);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
    }

    /** Cria um novo recebimento (sem id, receivedAt no momento atual). */
    public static GoodsReceipt create(Long supplierId, String warehouseCode, List<GoodsReceiptItem> items,
            String username) {
        return new GoodsReceipt(null, supplierId, warehouseCode, items, username, Instant.now());
    }

    /** Reconstitui um recebimento a partir de persistência. */
    public static GoodsReceipt of(Long id, Long supplierId, String warehouseCode, List<GoodsReceiptItem> items,
            String username, Instant receivedAt) {
        return new GoodsReceipt(id, supplierId, warehouseCode, items, username, receivedAt);
    }
}
