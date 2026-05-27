package com.securityspring.core.domain.exception.auth;

public class TotpChallengeExpiredException extends RuntimeException {
    public TotpChallengeExpiredException() {
        super("Desafio de autenticação expirado — faça login novamente");
    }
}
