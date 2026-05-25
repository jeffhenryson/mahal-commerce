package com.securityspring.core.domain.exception;

public class EmailAlreadyVerifiedException extends RuntimeException {
    public EmailAlreadyVerifiedException() {
        super("Email já verificado");
    }
}
