package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * Balanço de inventário (EST-F006). {@code warehouseCode} é resolvido pelo adapter — o domínio
 * guarda só o {@code warehouseId}.
 */
@Data
public class StockCountResponseDTO {
    private Long id;
    private String warehouseCode;
    /** {@code ABERTA}, {@code FECHADA} ou {@code CANCELADA}. */
    private String status;
    /** Quem abriu o balanço. */
    private String username;
    private Instant createdAt;
    /** Preenchido no fechamento ou no cancelamento. */
    private Instant closedAt;
    private List<StockCountItemResponseDTO> items;
}
