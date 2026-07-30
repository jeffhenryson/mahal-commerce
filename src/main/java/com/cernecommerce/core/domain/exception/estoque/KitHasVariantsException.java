package com.cernecommerce.core.domain.exception.estoque;

/**
 * Kit e variações são mutuamente exclusivos (EST-F015): um produto com grade de variações não
 * pode ser promovido a kit virtual.
 */
public class KitHasVariantsException extends RuntimeException {
    public KitHasVariantsException(String sku) {
        super("Produto com variações não pode ser kit: " + sku);
    }
}
