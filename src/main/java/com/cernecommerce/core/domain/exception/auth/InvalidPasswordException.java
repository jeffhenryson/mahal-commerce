package com.cernecommerce.core.domain.exception.auth;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException() {
        super("Current password is incorrect");
    }
}
