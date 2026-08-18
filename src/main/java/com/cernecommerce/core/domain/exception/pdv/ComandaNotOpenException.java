package com.cernecommerce.core.domain.exception.pdv;

import com.cernecommerce.core.domain.model.pdv.ComandaStatus;

public class ComandaNotOpenException extends RuntimeException {
    public ComandaNotOpenException(Long comandaId, ComandaStatus status) {
        super("Comanda " + comandaId + " não está aberta: " + status);
    }
}
