package com.securityspring.core.domain.exception.email;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String cause) {
        super("Failed to send verification email: " + cause);
    }
}
