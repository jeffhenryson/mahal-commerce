package com.securityspring.core.domain.exception.auth;

public class DevChallengeExpiredException extends RuntimeException {
    public DevChallengeExpiredException() {
        super("Desafio DEV expirado ou já utilizado");
    }
}
