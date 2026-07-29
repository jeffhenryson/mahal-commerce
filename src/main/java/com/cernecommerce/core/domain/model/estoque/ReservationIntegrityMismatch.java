package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Linha de diagnóstico de integridade (EST-C013): um par SKU/depósito cujo
 * {@link StockBalance#reservedQuantity()} diverge da soma das reservas {@code ACTIVE} para o
 * mesmo par em {@code stock_reservation}.
 *
 * <p>Diferente do órfão de SKU (EST-C011), essa divergência não aparece em nenhuma tela: o saldo
 * físico bate normalmente, só o disponível é que mente. O sintoma é <b>estoque travado
 * invisível</b> — a venda recusa por reserva e ninguém encontra a reserva que a justifique — mais
 * difícil de diagnosticar do que o overselling que a reserva existe para evitar.</p>
 *
 * <p>É um retrato de leitura, sem identidade própria: não é persistido e por isso não tem
 * {@code create()} — só {@link #of}, para reconstituição a partir da consulta de diagnóstico.</p>
 */
public record ReservationIntegrityMismatch(String sku, String warehouseCode,
        BigDecimal reservedQuantity, BigDecimal activeReservationsTotal) {

    public ReservationIntegrityMismatch {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        // Um lado sem linha nenhuma (contador zerado ou nenhuma reserva ACTIVE) devolve null da
        // consulta; aqui vira zero, para o consumidor não precisar checar.
        reservedQuantity = reservedQuantity == null ? BigDecimal.ZERO : reservedQuantity;
        activeReservationsTotal = activeReservationsTotal == null ? BigDecimal.ZERO : activeReservationsTotal;
    }

    /** Reconstitui uma linha do diagnóstico. */
    public static ReservationIntegrityMismatch of(String sku, String warehouseCode,
            BigDecimal reservedQuantity, BigDecimal activeReservationsTotal) {
        return new ReservationIntegrityMismatch(sku, warehouseCode, reservedQuantity, activeReservationsTotal);
    }

    /**
     * {@code reservedQuantity - activeReservationsTotal}. Positivo: o contador em
     * {@code stock_balance} está acima do ledger — saldo travado sem reserva ativa que o
     * justifique. Negativo: o ledger está acima do contador — o disponível está sendo
     * subestimado para mais do que deveria.
     */
    public BigDecimal difference() {
        return reservedQuantity.subtract(activeReservationsTotal);
    }
}
