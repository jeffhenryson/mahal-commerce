package com.cernecommerce.core.domain.exception.estoque;

/** Não há lata aberta para o par SKU/depósito consultado (EST-F027). */
public class OpenPackageNotFoundException extends RuntimeException {
    public OpenPackageNotFoundException(String sku, String warehouseCode) {
        super("Não há lata aberta de " + sku + " no depósito " + warehouseCode);
    }
}
