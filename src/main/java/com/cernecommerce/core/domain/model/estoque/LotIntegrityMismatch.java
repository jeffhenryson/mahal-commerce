package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Linha de diagnóstico de integridade (EST-F008): um par SKU/depósito de SKU lote-rastreado cujo
 * {@link StockBalance#quantity()} diverge da soma de {@link StockLot#quantity()} para o mesmo par.
 *
 * <p>{@code stock_lot} é aditivo — {@code SUM(stock_lot.quantity)} deve sempre igualar
 * {@code stock_balance.quantity}, mantido pela mesma transação de {@code adjustStock}, mas sem FK
 * que force a igualdade. O sintoma de drift não é overselling (o agregado continua sendo a fonte
 * de verdade para validar saída) — é o FEFO não achar de qual lote descontar uma saída que o
 * agregado já validou.</p>
 *
 * <p>É um retrato de leitura, sem identidade própria: não é persistido e por isso não tem
 * {@code create()} — só {@link #of}, para reconstituição a partir da consulta de diagnóstico.</p>
 */
public record LotIntegrityMismatch(String sku, String warehouseCode,
        BigDecimal balanceQuantity, BigDecimal lotsTotal) {

    public LotIntegrityMismatch {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        // Um lado sem linha nenhuma (saldo zerado ou nenhum stock_lot) devolve null da consulta;
        // aqui vira zero, para o consumidor não precisar checar.
        balanceQuantity = balanceQuantity == null ? BigDecimal.ZERO : balanceQuantity;
        lotsTotal = lotsTotal == null ? BigDecimal.ZERO : lotsTotal;
    }

    /** Reconstitui uma linha do diagnóstico. */
    public static LotIntegrityMismatch of(String sku, String warehouseCode,
            BigDecimal balanceQuantity, BigDecimal lotsTotal) {
        return new LotIntegrityMismatch(sku, warehouseCode, balanceQuantity, lotsTotal);
    }

    /**
     * {@code balanceQuantity - lotsTotal}. Positivo: o agregado está acima da soma dos lotes —
     * parte do físico não está em nenhum lote conhecido. Negativo: os lotes somam mais que o
     * agregado — sobra de lote que o agregado não reconhece.
     */
    public BigDecimal difference() {
        return balanceQuantity.subtract(lotsTotal);
    }
}
