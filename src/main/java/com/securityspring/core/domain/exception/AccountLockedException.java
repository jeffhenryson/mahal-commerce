package com.securityspring.core.domain.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String username) {
        super("Account temporarily locked due to too many failed attempts: " + username);
    }
}
