package com.cernecommerce.core.domain.exception.estoque;

public class KitSelfReferenceException extends RuntimeException {
    public KitSelfReferenceException(String kitSku) {
        super("Kit não pode ter a si mesmo como componente: " + kitSku);
    }
}
