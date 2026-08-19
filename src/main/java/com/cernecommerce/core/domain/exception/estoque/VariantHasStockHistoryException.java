package com.cernecommerce.core.domain.exception.estoque;

/**
 * Variante não pode ser excluída enquanto houver saldo ou movimentação de estoque gravados para
 * o seu SKU — {@code stock_balance}/{@code stock_movement} referenciam SKU como texto livre, sem
 * FK, então apagar a variante deixaria esse histórico órfão. Use
 * {@code PATCH .../variants/{variantSku} {"active": false}} para retirar a variante de circulação
 * preservando o histórico.
 */
public class VariantHasStockHistoryException extends RuntimeException {
    public VariantHasStockHistoryException(String productSku, String variantSku) {
        super("Variante " + variantSku + " do produto " + productSku
                + " não pode ser removida: há saldo ou movimentação de estoque gravados para este SKU");
    }
}
