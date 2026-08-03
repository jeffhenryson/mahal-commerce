package com.cernecommerce.core.domain.exception.estoque;

/**
 * Lote/validade informados onde não se aplicam (EST-F008): SKU não é lote-rastreado, ou a
 * movimentação não é uma ENTRADA (SAIDA consome por FEFO automaticamente, sem o chamador
 * escolher o lote).
 */
public class UnexpectedLotInfoException extends RuntimeException {
    public UnexpectedLotInfoException(String sku, String reason) {
        super("Informação de lote não se aplica ao SKU " + sku + ": " + reason);
    }
}
