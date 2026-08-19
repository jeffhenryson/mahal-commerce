package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item anotado na lista de reposição de um depósito (item 1 do pedido do frontend) — hoje um
 * rascunho de compra que vive em {@code localStorage} no navegador, sem compartilhamento entre
 * operadores e perdido ao limpar o navegador.
 *
 * <h2>Snapshot, não espelho ao vivo</h2>
 * <p>Os campos {@code *Snapshot} são deliberadamente congelados no momento do
 * {@code POST /estoque/replenishment-list/items}, <b>nunca recalculados na leitura</b>. A lista é
 * um rascunho de compra: se o saldo mudar depois de anotado, a intenção de compra continua
 * valendo e o histórico do porquê não se perde. {@code quantity} e {@code note} são os dois únicos
 * campos editáveis depois de anotado.</p>
 *
 * <p>Assim como {@code stock_balance}/{@code stock_movement}/{@code stock_reorder_point}, o
 * {@code sku} é texto livre, sem FK para {@code product} — mesma convenção do resto do módulo
 * (EST-C011).</p>
 */
public record ReplenishmentListItem(
        Long id,
        String sku,
        Long warehouseId,
        String productNameSnapshot,
        String categorySnapshot,
        String brandSnapshot,
        MeasurementUnit unitSnapshot,
        BigDecimal currentStockSnapshot,
        BigDecimal minStockSnapshot,
        BigDecimal suggestedQuantitySnapshot,
        BigDecimal quantity,
        BigDecimal unitCostSnapshot,
        BigDecimal previousPurchaseQuantitySnapshot,
        BigDecimal previousPurchaseUnitCostSnapshot,
        Instant previousPurchasedAtSnapshot,
        String note,
        Instant createdAt,
        String createdBy) {

    public ReplenishmentListItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity não pode ser negativa");
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy é obrigatório");
        }
    }

    /** Cria um novo item (sem id, createdAt no momento atual) — forma canônica do POST. */
    public static ReplenishmentListItem create(String sku, Long warehouseId, String productNameSnapshot,
            String categorySnapshot, String brandSnapshot, MeasurementUnit unitSnapshot,
            BigDecimal currentStockSnapshot, BigDecimal minStockSnapshot, BigDecimal suggestedQuantitySnapshot,
            BigDecimal quantity, BigDecimal unitCostSnapshot, BigDecimal previousPurchaseQuantitySnapshot,
            BigDecimal previousPurchaseUnitCostSnapshot, Instant previousPurchasedAtSnapshot, String note,
            String createdBy) {
        return new ReplenishmentListItem(null, sku, warehouseId, productNameSnapshot, categorySnapshot,
                brandSnapshot, unitSnapshot, currentStockSnapshot, minStockSnapshot, suggestedQuantitySnapshot,
                quantity, unitCostSnapshot, previousPurchaseQuantitySnapshot, previousPurchaseUnitCostSnapshot,
                previousPurchasedAtSnapshot, note, Instant.now(), createdBy);
    }

    /** Reconstitui a partir de persistência. */
    public static ReplenishmentListItem of(Long id, String sku, Long warehouseId, String productNameSnapshot,
            String categorySnapshot, String brandSnapshot, MeasurementUnit unitSnapshot,
            BigDecimal currentStockSnapshot, BigDecimal minStockSnapshot, BigDecimal suggestedQuantitySnapshot,
            BigDecimal quantity, BigDecimal unitCostSnapshot, BigDecimal previousPurchaseQuantitySnapshot,
            BigDecimal previousPurchaseUnitCostSnapshot, Instant previousPurchasedAtSnapshot, String note,
            Instant createdAt, String createdBy) {
        return new ReplenishmentListItem(id, sku, warehouseId, productNameSnapshot, categorySnapshot, brandSnapshot,
                unitSnapshot, currentStockSnapshot, minStockSnapshot, suggestedQuantitySnapshot, quantity,
                unitCostSnapshot, previousPurchaseQuantitySnapshot, previousPurchaseUnitCostSnapshot,
                previousPurchasedAtSnapshot, note, createdAt, createdBy);
    }

    /**
     * Altera os dois únicos campos editáveis depois de anotado — {@code quantity} e {@code note}.
     * {@code null} em {@code newNote} apaga a nota (distinto do resto do módulo: aqui não há
     * "manter" implícito porque os dois campos sempre chegam juntos no PATCH do frontend).
     */
    public ReplenishmentListItem withQuantityAndNote(BigDecimal newQuantity, String newNote) {
        return new ReplenishmentListItem(id, sku, warehouseId, productNameSnapshot, categorySnapshot, brandSnapshot,
                unitSnapshot, currentStockSnapshot, minStockSnapshot, suggestedQuantitySnapshot,
                newQuantity == null ? quantity : newQuantity, unitCostSnapshot, previousPurchaseQuantitySnapshot,
                previousPurchaseUnitCostSnapshot, previousPurchasedAtSnapshot, newNote, createdAt, createdBy);
    }
}
