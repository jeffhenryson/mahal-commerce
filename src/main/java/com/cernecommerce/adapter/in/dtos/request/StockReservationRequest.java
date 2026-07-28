package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockReservationRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @NotBlank
    @Size(min = 2, max = 50)
    private String warehouseCode;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    /**
     * Identificador de quem está reservando — o checkout do chamador hoje, o pedido quando o
     * domínio existir. É por ele que se libera ou consome o conjunto todo de uma vez.
     */
    @NotBlank
    @Size(min = 3, max = 80)
    private String ownerReference;

    /**
     * Validade em minutos. Ausente usa o padrão configurado (30 min). Teto de 24h: reserva mais
     * longa que isso é saldo travado que ninguém vai lembrar de conferir.
     */
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(1440)
    private Integer ttlMinutes;
}
