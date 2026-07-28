package com.cernecommerce.core.domain.exception.pdv;

/** O operador não tem caixa aberto (PDV-F001). */
public class NoOpenCashRegisterSessionException extends RuntimeException {

    public NoOpenCashRegisterSessionException(String operator) {
        super("O operador " + operator + " não tem caixa aberto.");
    }
}
