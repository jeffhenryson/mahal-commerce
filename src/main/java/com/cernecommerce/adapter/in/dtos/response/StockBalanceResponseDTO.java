package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockBalanceResponseDTO {
    private String sku;
    private String warehouseCode;
    private BigDecimal quantity;
}
