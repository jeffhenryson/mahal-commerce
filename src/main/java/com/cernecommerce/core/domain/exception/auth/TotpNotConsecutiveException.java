package com.cernecommerce.core.domain.exception.auth;

public class TotpNotConsecutiveException extends RuntimeException {
    public TotpNotConsecutiveException() {
        super("O segundo código deve ser do período imediatamente seguinte ao primeiro");
    }
}
