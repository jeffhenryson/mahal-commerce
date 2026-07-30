package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashbackMarginImpactResponseDTO {
    private String sku;
    private String name;
    private BigDecimal marginPercent;
    private BigDecimal cashbackPercent;
    /** {@code cashbackPercent / marginPercent * 100} — a fatia da margem que a taxa consome. */
    private BigDecimal marginShareConsumed;
}
