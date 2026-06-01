package com.securityspring.core.domain.exception.auth;

public class TotpCodeRequiredException extends RuntimeException {
    public TotpCodeRequiredException() {
        super("Código 2FA obrigatório para esta operação");
    }
}
