package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Um lote de um SKU lote-rastreado num depósito (EST-F008). */
@Data
public class StockLotResponseDTO {
    private Long id;
    private String sku;
    private String warehouseCode;
    private String lotCode;
    private LocalDate expiryDate;
    private BigDecimal quantity;
    /** Instante do último alerta de vencimento disparado; nulo se ainda não alertado. */
    private Instant alertedAt;
}
