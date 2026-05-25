package com.securityspring.core.domain.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String username) {
        super("Conta bloqueada temporariamente por excesso de tentativas: " + username);
    }
}
