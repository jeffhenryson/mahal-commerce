package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Linha de diagnóstico de integridade (EST-C011): um par SKU/depósito que tem saldo,
 * movimentações ou ponto de reposição gravados, mas cujo SKU <b>não existe</b> no catálogo —
 * nem como {@link Product#sku()} nem como {@link ProductVariant#sku()}.
 *
 * <p>Desde EST-C002 nenhuma escrita nova pode criar um órfão: {@code adjustStock} e
 * {@code setReorderPoint} exigem SKU conhecido. Este record existe para levantar o passivo
 * gravado <b>antes</b> daquela correção, já que não há FK de {@code stock_balance} /
 * {@code stock_movement} / {@code stock_reorder_point} para {@code product}.</p>
 *
 * <p>É um retrato de leitura, não uma entidade: não tem identidade própria, não é persistido
 * e por isso não tem {@code create()} — só {@link #of}, para reconstituição a partir da
 * consulta de diagnóstico.</p>
 */
public record OrphanSku(String sku, String warehouseCode, BigDecimal quantity, long movementCount,
        boolean hasReorderPoint, Instant lastMovementAt) {

    public OrphanSku {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (movementCount < 0) {
            throw new IllegalArgumentException("movementCount não pode ser negativo");
        }
        // Um órfão presente só em stock_movement ou só em stock_reorder_point não tem linha de
        // saldo; a consulta devolve null e aqui vira zero, para o consumidor não precisar checar.
        quantity = quantity == null ? BigDecimal.ZERO : quantity;
    }

    /**
     * Reconstitui uma linha do diagnóstico. {@code lastMovementAt} é nulo quando o par nunca
     * foi movimentado — caso de órfão que só tem saldo ou só tem ponto de reposição.
     */
    public static OrphanSku of(String sku, String warehouseCode, BigDecimal quantity, long movementCount,
            boolean hasReorderPoint, Instant lastMovementAt) {
        return new OrphanSku(sku, warehouseCode, quantity, movementCount, hasReorderPoint, lastMovementAt);
    }
}
