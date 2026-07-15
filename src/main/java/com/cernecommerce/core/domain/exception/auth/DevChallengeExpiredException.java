package com.cernecommerce.core.domain.exception.auth;

public class DevChallengeExpiredException extends RuntimeException {
    public DevChallengeExpiredException() {
        super("Desafio DEV expirado ou já utilizado");
    }
}
