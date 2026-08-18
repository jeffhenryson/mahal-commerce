package com.cernecommerce.core.domain.exception.pdv;

public class ComandaNotFoundException extends RuntimeException {
    public ComandaNotFoundException(Long comandaId) {
        super("Comanda não encontrada: " + comandaId);
    }
}
