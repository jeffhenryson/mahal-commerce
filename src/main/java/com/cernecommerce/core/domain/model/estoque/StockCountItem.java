package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Um SKU contado dentro de um balanço (EST-F006).
 *
 * <p>{@code expectedQuantity} e {@code difference} só são preenchidos no <b>fechamento</b>: são o
 * retrato do saldo do sistema no instante em que o ajuste foi aplicado. Guardá-los é o que
 * permite auditar a divergência depois — uma vez ajustado, o saldo já não conta essa história.</p>
 *
 * <p>{@code lotCode} (EST-F008) é nulo para SKU não lote-rastreado — nesse caso a contagem é
 * agregada, como sempre foi. Para SKU lote-rastreado, cada lote é contado e reconciliado
 * separadamente: {@code expectedQuantity}/{@code difference} do fechamento comparam contra o saldo
 * daquele lote em {@code StockLot}, não contra o agregado de {@code stock_balance}.</p>
 *
 * @param countedQuantity  o que foi contado na prateleira; nunca negativo, e zero é válido
 * @param expectedQuantity saldo do sistema no fechamento (do lote, se {@code lotCode} != null; do
 *                         agregado, senão); {@code null} enquanto a contagem está aberta
 * @param difference       {@code countedQuantity - expectedQuantity}; negativo significa falta
 * @param lotCode          lote contado; {@code null} para SKU não lote-rastreado
 */
public record StockCountItem(Long id, String sku, BigDecimal countedQuantity,
        BigDecimal expectedQuantity, BigDecimal difference, String lotCode) {

    public StockCountItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new IllegalArgumentException("countedQuantity não pode ser negativa");
        }
    }

    /** Compatibilidade com chamadores anteriores a EST-F008 — equivale a {@code lotCode = null}. */
    public StockCountItem(Long id, String sku, BigDecimal countedQuantity, BigDecimal expectedQuantity,
            BigDecimal difference) {
        this(id, sku, countedQuantity, expectedQuantity, difference, null);
    }

    /** Item recém-contado, ainda sem confronto com o saldo do sistema. SKU não lote-rastreado. */
    public static StockCountItem counted(String sku, BigDecimal countedQuantity) {
        return new StockCountItem(null, sku, countedQuantity, null, null, null);
    }

    /** Item recém-contado de um lote específico (EST-F008), ainda sem confronto. */
    public static StockCountItem counted(String sku, BigDecimal countedQuantity, String lotCode) {
        return new StockCountItem(null, sku, countedQuantity, null, null, lotCode);
    }

    /** Reconstitui a partir de persistência. SKU não lote-rastreado. */
    public static StockCountItem of(Long id, String sku, BigDecimal countedQuantity,
            BigDecimal expectedQuantity, BigDecimal difference) {
        return new StockCountItem(id, sku, countedQuantity, expectedQuantity, difference, null);
    }

    /** Reconstitui a partir de persistência, com lote (EST-F008). */
    public static StockCountItem of(Long id, String sku, BigDecimal countedQuantity,
            BigDecimal expectedQuantity, BigDecimal difference, String lotCode) {
        return new StockCountItem(id, sku, countedQuantity, expectedQuantity, difference, lotCode);
    }

    /** Carimba o saldo esperado e calcula a divergência. Usado no fechamento do balanço. */
    public StockCountItem reconciledWith(BigDecimal systemQuantity) {
        return new StockCountItem(id, sku, countedQuantity, systemQuantity,
                countedQuantity.subtract(systemQuantity), lotCode);
    }

    /**
     * {@code true} se o contado difere do saldo do sistema. Só itens divergentes geram
     * movimentação no fechamento — contagem que bateu não polui o ledger.
     */
    public boolean diverges() {
        return difference != null && difference.signum() != 0;
    }
}
