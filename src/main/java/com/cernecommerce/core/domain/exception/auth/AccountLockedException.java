package com.cernecommerce.core.domain.exception.auth;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String username) {
        super("Account temporarily locked due to too many failed attempts: " + username);
    }
}
