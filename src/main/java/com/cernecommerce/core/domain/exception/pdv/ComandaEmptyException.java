package com.cernecommerce.core.domain.exception.pdv;

public class ComandaEmptyException extends RuntimeException {
    public ComandaEmptyException(Long comandaId) {
        super("Comanda " + comandaId + " não tem itens lançados — não há o que fechar");
    }
}
