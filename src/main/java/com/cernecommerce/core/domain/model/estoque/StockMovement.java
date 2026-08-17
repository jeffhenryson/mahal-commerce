package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro auditável de uma movimentação manual de estoque (entrada, saída ou ajuste) que
 * altera o {@link StockBalance} de um SKU em um depósito.
 *
 * @param lotCode lote de origem (EST-F008), só preenchido em {@code ENTRADA} de SKU
 *        lote-rastreado. {@code null} em toda {@code SAIDA}/{@code AJUSTE} e em qualquer
 *        movimentação de SKU não lote-rastreado — uma {@code SAIDA} pode atravessar vários lotes
 *        via FEFO, então não força uma relação 1:1 entre movimentação e lote.
 * @param unitCost custo unitário da entrada (EST-F007), opcional mesmo em {@code ENTRADA} — nem
 *        toda entrada tem custo conhecido no momento (ex.: balanço de inventário). Alimenta o
 *        recálculo de {@link StockBalance#averageCost()} e fica gravado aqui para auditoria.
 *        {@code null} em toda {@code SAIDA}/{@code AJUSTE}.
 */
public record StockMovement(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
        String reason, String username, Instant createdAt, String lotCode, BigDecimal unitCost) {

    public StockMovement {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        // AJUSTE grava o saldo contado, não um delta (EST-C009), e contar zero é legítimo:
        // item que acabou ou sumiu. Para ENTRADA e SAIDA, movimento de quantidade zero
        // continua sem sentido.
        if (quantity == null || quantity.signum() < 0
                || (quantity.signum() == 0 && type != MovementType.AJUSTE)) {
            throw new IllegalArgumentException(type == MovementType.AJUSTE
                    ? "quantity de AJUSTE não pode ser negativa"
                    : "quantity deve ser maior que zero");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason é obrigatório");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
    }

    /** Cria uma nova movimentação sem lote nem custo (sem id, createdAt no momento atual). */
    public static StockMovement create(String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username) {
        return create(sku, warehouseId, type, quantity, reason, username, null);
    }

    /** Cria uma nova movimentação com o lote de origem (EST-F008, ENTRADA lote-rastreada), sem custo. */
    public static StockMovement create(String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode) {
        return create(sku, warehouseId, type, quantity, reason, username, lotCode, null);
    }

    /** Cria uma nova movimentação com lote e custo unitário de entrada (EST-F007/EST-F008). */
    public static StockMovement create(String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, BigDecimal unitCost) {
        return new StockMovement(null, sku, warehouseId, type, quantity, reason, username, Instant.now(), lotCode,
                unitCost);
    }

    /** Reconstitui uma movimentação sem lote nem custo a partir de persistência. */
    public static StockMovement of(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username, Instant createdAt) {
        return of(id, sku, warehouseId, type, quantity, reason, username, createdAt, null);
    }

    /** Reconstitui uma movimentação a partir de persistência, com lote explícito e sem custo. */
    public static StockMovement of(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username, Instant createdAt, String lotCode) {
        return of(id, sku, warehouseId, type, quantity, reason, username, createdAt, lotCode, null);
    }

    /** Reconstitui uma movimentação a partir de persistência — forma canônica, com lote e custo. */
    public static StockMovement of(Long id, String sku, Long warehouseId, MovementType type, BigDecimal quantity,
            String reason, String username, Instant createdAt, String lotCode, BigDecimal unitCost) {
        return new StockMovement(id, sku, warehouseId, type, quantity, reason, username, createdAt, lotCode,
                unitCost);
    }
}
