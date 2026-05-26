package com.securityspring.core.domain.exception;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String cause) {
        super("Falha ao enviar email de verificação: " + cause);
    }
}
