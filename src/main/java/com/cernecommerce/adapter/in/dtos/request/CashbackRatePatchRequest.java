package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** Alteração parcial de taxa de cashback. Campo ausente ou nulo significa <b>não mexer</b>. */
@Data
public class CashbackRatePatchRequest {

    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal percent;

    private Boolean active;

    private Instant validTo;
}
