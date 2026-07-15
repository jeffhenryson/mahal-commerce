package com.cernecommerce.core.domain.exception.auth;

public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException() {
        super("Sessão não encontrada");
    }
}
