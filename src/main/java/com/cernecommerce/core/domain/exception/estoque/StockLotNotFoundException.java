package com.cernecommerce.core.domain.exception.estoque;

/**
 * O {@code lotCode} contado num balanço (EST-F008) não corresponde a nenhum lote existente para o
 * par SKU/depósito. Balanço só reconcilia lote que já existe — lote novo entra por recebimento
 * ({@code adjustStock} ENTRADA), não pela contagem.
 */
public class StockLotNotFoundException extends RuntimeException {
    public StockLotNotFoundException(String sku, String lotCode) {
        super("Lote não encontrado para o SKU " + sku + ": " + lotCode);
    }
}
