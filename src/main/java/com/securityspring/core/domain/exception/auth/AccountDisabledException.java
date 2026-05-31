package com.securityspring.core.domain.exception.auth;

public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException(String username) {
        super("Account is disabled: " + username);
    }
}
