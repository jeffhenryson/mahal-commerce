package com.security_spring.core.domain.exception;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException() {
        super("Current password is incorrect");
    }
}
