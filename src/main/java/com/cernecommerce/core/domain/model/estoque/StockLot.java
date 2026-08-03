package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Subdivisão por lote do saldo de um SKU lote-rastreado ({@link Product#lotTracked()}) num
 * {@link Warehouse} (EST-F008).
 *
 * <h2>Aditivo, não substituto de {@link StockBalance}</h2>
 * <p>{@code stock_balance.quantity} continua sendo o agregado — escrito, lido e travado
 * (optimistic locking) exatamente como antes de EST-F008. {@code StockLot} é a mesma quantidade
 * quebrada por lote: {@code SUM(stock_lot.quantity)} para o par (sku, warehouse) deve sempre
 * igualar {@code stock_balance.quantity}, mantido pela mesma transação de
 * {@code EstoqueService.adjustStock} que já escreve saldo e ledger juntos. Não é uma tabela
 * derivada (como o saldo de kit) nem uma reescrita do agregado — é mais uma peça na mesma
 * transação.</p>
 *
 * <p>Por isso {@code StockLot} não precisa do seu próprio mecanismo de concorrência: toda escrita
 * num lote acontece dentro de uma transação que também escreve {@code stock_balance}, e o
 * {@code @Version} de {@code stock_balance} já serializa duas operações concorrentes no mesmo par
 * SKU/depósito. O {@link #version()} aqui é só para manter o padrão do resto do módulo.</p>
 *
 * @param lotCode identificador do lote, definido por quem recebe a mercadoria (não gerado pelo
 *        sistema) — é o número que está na caixa/nota do fornecedor.
 * @param expiryDate validade do lote. Imutável depois de criado: um reabastecimento do mesmo
 *        {@code lotCode} com validade diferente é um erro de digitação, não uma atualização.
 * @param alertedAt instante em que o alerta de vencimento (EST-F008) já foi disparado para este
 *        lote — {@code null} enquanto não alertado. Existe para o alerta não repetir todo dia
 *        enquanto o lote não é consumido.
 */
public record StockLot(Long id, String sku, Long warehouseId, String lotCode, LocalDate expiryDate,
        BigDecimal quantity, Instant alertedAt, long version) {

    public StockLot {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (warehouseId == null) {
            throw new IllegalArgumentException("warehouseId é obrigatório");
        }
        if (lotCode == null || lotCode.isBlank()) {
            throw new IllegalArgumentException("lotCode é obrigatório");
        }
        if (expiryDate == null) {
            throw new IllegalArgumentException("expiryDate é obrigatório");
        }
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("quantity não pode ser negativa");
        }
    }

    /** Novo lote, ainda sem estoque recebido — o primeiro {@link #receive(BigDecimal)} soma. */
    public static StockLot create(String sku, Long warehouseId, String lotCode, LocalDate expiryDate) {
        return new StockLot(null, sku, warehouseId, lotCode, expiryDate, BigDecimal.ZERO, null, 0L);
    }

    /** Reconstitui um lote a partir de persistência. */
    public static StockLot of(Long id, String sku, Long warehouseId, String lotCode, LocalDate expiryDate,
            BigDecimal quantity, Instant alertedAt, long version) {
        return new StockLot(id, sku, warehouseId, lotCode, expiryDate, quantity, alertedAt, version);
    }

    /** ENTRADA no lote: soma a quantidade recebida. */
    public StockLot receive(BigDecimal receivedQuantity) {
        if (receivedQuantity == null || receivedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("quantidade recebida deve ser maior que zero");
        }
        return new StockLot(id, sku, warehouseId, lotCode, expiryDate, quantity.add(receivedQuantity),
                alertedAt, version);
    }

    /**
     * Consome (parte d)o lote — tipicamente uma fatia de uma SAIDA distribuída por FEFO entre
     * vários lotes. Quem decide quanto pedir de cada lote é o service (do que vence primeiro em
     * diante); esta validação é a rede de segurança contra pedir mais do que o próprio lote tem,
     * não a checagem de saldo total — essa é {@link StockBalance#apply}.
     */
    public StockLot consume(BigDecimal consumedQuantity) {
        if (consumedQuantity == null || consumedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("quantidade consumida deve ser maior que zero");
        }
        if (consumedQuantity.compareTo(quantity) > 0) {
            throw new InsufficientStockException(sku, warehouseId, quantity, consumedQuantity);
        }
        return new StockLot(id, sku, warehouseId, lotCode, expiryDate, quantity.subtract(consumedQuantity),
                alertedAt, version);
    }

    /**
     * Fechamento de balanço de inventário (EST-F006/EST-F008): substitui a quantidade pelo valor
     * contado na prateleira — saldo-alvo, mesmo espírito de {@code StockBalance.apply(AJUSTE)},
     * só que por lote em vez de por SKU inteiro.
     */
    public StockLot reconciledTo(BigDecimal countedQuantity) {
        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new IllegalArgumentException("quantidade contada não pode ser negativa");
        }
        return new StockLot(id, sku, warehouseId, lotCode, expiryDate, countedQuantity, alertedAt, version);
    }

    /** Carimba o instante do alerta de vencimento, para o scheduler não repetir amanhã. */
    public StockLot alerted() {
        return new StockLot(id, sku, warehouseId, lotCode, expiryDate, quantity, Instant.now(), version);
    }

    /** {@code true} se o lote vence em {@code cutoff} ou antes — inclui lote já vencido. */
    public boolean isExpiringBy(LocalDate cutoff) {
        return !expiryDate.isAfter(cutoff);
    }

    /** {@code true} se o alerta de vencimento já foi disparado para este lote. */
    public boolean alreadyAlerted() {
        return alertedAt != null;
    }
}
