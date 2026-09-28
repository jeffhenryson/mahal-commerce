package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * Filtros de {@code GET /pdv/sessions} (PDV-F026). Todos opcionais; {@code from}/{@code to} incidem
 * sobre {@code openedAt}, inclusivos.
 */
public record CashRegisterSessionFilter(CashRegisterSession.Status status, Instant from, Instant to,
        String operator) {

    public CashRegisterSessionFilter {
        operator = operator == null || operator.isBlank() ? null : operator.trim();
    }

    public static CashRegisterSessionFilter none() {
        return new CashRegisterSessionFilter(null, null, null, null);
    }
}
