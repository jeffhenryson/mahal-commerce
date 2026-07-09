package com.cernecommerce.core.domain.exception.auth;

public class TotpAlreadyEnabledException extends RuntimeException {
    public TotpAlreadyEnabledException() {
        super("Autenticação em dois fatores já está ativada");
    }
}
