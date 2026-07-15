package com.cernecommerce.core.domain.exception.auth;

public class TotpSetupRequiredException extends RuntimeException {
    public TotpSetupRequiredException() {
        super("Autenticação de dois fatores é obrigatória — configure o 2FA antes de continuar");
    }
}
