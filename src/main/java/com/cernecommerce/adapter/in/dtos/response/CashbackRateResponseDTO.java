package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CashbackRateResponseDTO {
    private Long id;
    /** {@code GLOBAL}, {@code CATEGORY} ou {@code SKU}. */
    private String scope;
    private String scopeRef;
    private BigDecimal percent;
    private boolean active;
    private Instant validFrom;
    private Instant validTo;
    private Instant createdAt;
}
