package com.cernecommerce.core.domain.exception.compras;

import java.util.List;

/**
 * Confirmação de import de NF-e (EST-F005) com alguma linha ainda sem SKU resolvido — nem por
 * EAN, nem por override manual do operador.
 */
public class UnmatchedNfeLineException extends RuntimeException {
    public UnmatchedNfeLineException(List<Integer> itemNumbers) {
        super("Linhas sem SKU resolvido, faltando override: " + itemNumbers);
    }
}
