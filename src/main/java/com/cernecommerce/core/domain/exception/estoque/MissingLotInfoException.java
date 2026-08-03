package com.cernecommerce.core.domain.exception.estoque;

/** SKU lote-rastreado (EST-F008) recebeu ENTRADA sem informar lote e validade. */
public class MissingLotInfoException extends RuntimeException {
    public MissingLotInfoException(String sku) {
        super("SKU " + sku + " é lote-rastreado: ENTRADA exige lotCode e expiryDate");
    }
}
