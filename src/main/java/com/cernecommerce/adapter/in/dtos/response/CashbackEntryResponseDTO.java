package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CashbackEntryResponseDTO {
    private Long id;
    private Long customerId;
    private Long orderId;
    private Long orderItemId;
    /** {@code EARNED}, {@code REDEEMED}, {@code REVERSED} ou {@code EXPIRED}. */
    private String type;
    private BigDecimal amount;
    private Instant availableAt;
    private Instant expiresAt;
    private Long reversesEntryId;
    private Instant createdAt;
}
