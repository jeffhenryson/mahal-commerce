package com.cernecommerce.core.domain.exception.estoque;

import com.cernecommerce.core.domain.model.estoque.ProductType;

/**
 * Componente de kit precisa ser {@link ProductType#SIMPLES} — kit dentro de kit é proibido por
 * construção (EST-F015, §2.10: um nível só).
 */
public class KitComponentNotSimpleException extends RuntimeException {
    public KitComponentNotSimpleException(String kitSku, String componentSku, ProductType actualType) {
        super("Componente " + componentSku + " do kit " + kitSku + " precisa ser SIMPLES, é " + actualType);
    }
}
