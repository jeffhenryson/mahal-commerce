package com.cernecommerce.core.domain.model.estoque;

/**
 * Estado de uma {@link StockReservation} (EST-F021).
 *
 * <p>{@link #ACTIVE} é o único estado que segura saldo. Os outros três são terminais e existem
 * separados de propósito: liberar por expiração, por cancelamento do pedido e por consumo na venda
 * são a mesma escrita de saldo, mas três histórias diferentes na hora de investigar por que uma
 * unidade sumiu do disponível.</p>
 */
public enum ReservationStatus {

    /** Segurando saldo. Conta em {@code StockBalance.reservedQuantity}. */
    ACTIVE,

    /** Virou saída de verdade — o pagamento foi confirmado e a mercadoria saiu. */
    CONSUMED,

    /** Devolvida ao disponível por cancelamento explícito, antes de vencer. */
    RELEASED,

    /** Devolvida ao disponível pelo varredor de expiração, por ter passado do {@code expiresAt}. */
    EXPIRED;

    /** Indica se a reserva ainda segura saldo. */
    public boolean isActive() {
        return this == ACTIVE;
    }
}
