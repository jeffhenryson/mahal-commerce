package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GoodsReceiptItemResponseDTO {
    private String sku;
    private BigDecimal quantity;
    private String lotCode;
    private LocalDate expiryDate;
}
