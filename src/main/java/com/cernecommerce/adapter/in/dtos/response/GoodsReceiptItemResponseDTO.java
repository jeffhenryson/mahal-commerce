package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoodsReceiptItemResponseDTO {
    private String sku;
    private BigDecimal quantity;
}
