package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleItemResponseDTO {
    private String sku;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
}
