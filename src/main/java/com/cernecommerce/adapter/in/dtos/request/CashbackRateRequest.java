package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class CashbackRateRequest {

    /** {@code GLOBAL}, {@code CATEGORY} ou {@code SKU}. Valor desconhecido é 400. */
    @NotBlank
    private String scope;

    /** Obrigatório para {@code CATEGORY}/{@code SKU}; deve vir ausente para {@code GLOBAL}. */
    @Size(max = 100)
    private String scopeRef;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    private BigDecimal percent;

    /** Omitido, vale a partir de agora. */
    private Instant validFrom;

    /** Omitido, vigência em aberto. */
    private Instant validTo;
}
