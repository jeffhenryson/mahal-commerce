package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Um SKU contado dentro de um balanço (EST-F006).
 *
 * <p>{@code expectedQuantity} e {@code difference} só são preenchidos no <b>fechamento</b>: são o
 * retrato do saldo do sistema no instante em que o ajuste foi aplicado. Guardá-los é o que
 * permite auditar a divergência depois — uma vez ajustado, o saldo já não conta essa história.</p>
 *
 * @param countedQuantity  o que foi contado na prateleira; nunca negativo, e zero é válido
 * @param expectedQuantity saldo do sistema no fechamento; {@code null} enquanto a contagem está aberta
 * @param difference       {@code countedQuantity - expectedQuantity}; negativo significa falta
 */
public record StockCountItem(Long id, String sku, BigDecimal countedQuantity,
        BigDecimal expectedQuantity, BigDecimal difference) {

    public StockCountItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (countedQuantity == null || countedQuantity.signum() < 0) {
            throw new IllegalArgumentException("countedQuantity não pode ser negativa");
        }
    }

    /** Item recém-contado, ainda sem confronto com o saldo do sistema. */
    public static StockCountItem counted(String sku, BigDecimal countedQuantity) {
        return new StockCountItem(null, sku, countedQuantity, null, null);
    }

    /** Reconstitui a partir de persistência. */
    public static StockCountItem of(Long id, String sku, BigDecimal countedQuantity,
            BigDecimal expectedQuantity, BigDecimal difference) {
        return new StockCountItem(id, sku, countedQuantity, expectedQuantity, difference);
    }

    /** Carimba o saldo esperado e calcula a divergência. Usado no fechamento do balanço. */
    public StockCountItem reconciledWith(BigDecimal systemQuantity) {
        return new StockCountItem(id, sku, countedQuantity, systemQuantity,
                countedQuantity.subtract(systemQuantity));
    }

    /**
     * {@code true} se o contado difere do saldo do sistema. Só itens divergentes geram
     * movimentação no fechamento — contagem que bateu não polui o ledger.
     */
    public boolean diverges() {
        return difference != null && difference.signum() != 0;
    }
}
