package com.securityspring.core.domain.exception.auth;

public class TotpNotEnabledException extends RuntimeException {
    public TotpNotEnabledException() {
        super("Autenticação em dois fatores não está ativada");
    }
}
