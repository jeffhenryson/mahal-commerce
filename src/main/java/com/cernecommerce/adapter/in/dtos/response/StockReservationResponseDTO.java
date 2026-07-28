package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reserva de estoque (EST-F021). {@code warehouseCode} é resolvido pelo adapter — o domínio guarda
 * só o {@code warehouseId}, como no balanço de inventário.
 */
@Data
public class StockReservationResponseDTO {
    private Long id;
    private String sku;
    private String warehouseCode;
    private BigDecimal quantity;
    /** Quem pediu a reserva; usado para liberar ou consumir o conjunto todo de um pedido. */
    private String ownerReference;
    /** {@code ACTIVE}, {@code CONSUMED}, {@code RELEASED} ou {@code EXPIRED}. */
    private String status;
    /** A partir daqui o varredor devolve a quantidade ao disponível. */
    private Instant expiresAt;
    private Instant createdAt;
    /** Preenchido no consumo, na liberação ou na expiração. */
    private Instant resolvedAt;
    private String username;
}
