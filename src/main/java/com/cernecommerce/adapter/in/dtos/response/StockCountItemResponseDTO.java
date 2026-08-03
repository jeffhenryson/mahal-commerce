package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Um SKU dentro de um balanço (EST-F006). {@code expectedQuantity} e {@code difference} vêm nulos
 * enquanto a contagem está aberta — só o fechamento confronta com o saldo do sistema.
 */
@Data
public class StockCountItemResponseDTO {
    private Long id;
    private String sku;
    private BigDecimal countedQuantity;
    /** Saldo do sistema no instante do fechamento. */
    private BigDecimal expectedQuantity;
    /** {@code countedQuantity - expectedQuantity}: negativo é falta, positivo é sobra. */
    private BigDecimal difference;
    /** Lote contado (EST-F008); nulo para SKU não lote-rastreado. */
    private String lotCode;
}
