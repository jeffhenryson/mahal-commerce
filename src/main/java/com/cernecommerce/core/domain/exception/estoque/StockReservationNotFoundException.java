package com.cernecommerce.core.domain.exception.estoque;

/** Reserva de estoque inexistente (EST-F021). */
public class StockReservationNotFoundException extends RuntimeException {

    public StockReservationNotFoundException(Long id) {
        super("Reserva de estoque " + id + " não encontrada");
    }
}
