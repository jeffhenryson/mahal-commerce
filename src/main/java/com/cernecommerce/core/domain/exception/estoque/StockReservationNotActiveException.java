package com.cernecommerce.core.domain.exception.estoque;

import com.cernecommerce.core.domain.model.estoque.ReservationStatus;

/**
 * Tentativa de consumir ou liberar uma reserva que já não está {@link ReservationStatus#ACTIVE}
 * (EST-F021).
 *
 * <p>Existe pela mesma razão de {@code StockCountNotOpenException}: consumir é a operação que baixa
 * saldo, e deixá-la repetir sobre uma reserva já resolvida daria baixa duas vezes na mesma
 * mercadoria. A checagem mora no service, num lugar só.</p>
 */
public class StockReservationNotActiveException extends RuntimeException {

    public StockReservationNotActiveException(Long id, ReservationStatus status) {
        super("Reserva de estoque " + id + " não está ativa (status atual: " + status + ")");
    }
}
