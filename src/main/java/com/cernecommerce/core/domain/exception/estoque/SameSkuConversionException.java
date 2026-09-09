package com.cernecommerce.core.domain.exception.estoque;

/**
 * Conversão de um SKU para ele mesmo (EST-F025).
 *
 * <p>Não é apenas inútil — é uma saída seguida de uma entrada do mesmo saldo, que se anulam e ainda
 * assim deixam duas linhas no ledger descrevendo um movimento que não houve. Quem quer corrigir
 * saldo usa {@code AJUSTE}; quem quer converter usa dois SKUs.</p>
 */
public class SameSkuConversionException extends RuntimeException {

    public SameSkuConversionException(String sku) {
        super("Conversão exige SKUs distintos: origem e destino são ambos " + sku);
    }
}
