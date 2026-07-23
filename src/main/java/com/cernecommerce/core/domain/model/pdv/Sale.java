package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Venda de balcão registrada dentro de uma {@link CashRegisterSession}, com baixa
 * automática correspondente no estoque (uma {@code StockMovement} de SAIDA por item,
 * via {@code EstoqueUseCase.adjustStock}).
 */
public record Sale(Long id, Long sessionId, String warehouseCode, List<SaleItem> items, BigDecimal totalAmount,
        Instant soldAt) {

    public Sale {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items não pode ser vazio");
        }
        items = List.copyOf(items);
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("totalAmount não pode ser negativo");
        }
    }

    /** Cria uma nova venda (sem id, soldAt no momento atual, totalAmount somado dos items). */
    public static Sale create(Long sessionId, String warehouseCode, List<SaleItem> items) {
        BigDecimal total = items == null ? BigDecimal.ZERO
                : items.stream().map(SaleItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Sale(null, sessionId, warehouseCode, items, total, Instant.now());
    }

    /** Reconstitui uma venda a partir de persistência. */
    public static Sale of(Long id, Long sessionId, String warehouseCode, List<SaleItem> items,
            BigDecimal totalAmount, Instant soldAt) {
        return new Sale(id, sessionId, warehouseCode, items, totalAmount, soldAt);
    }
}
