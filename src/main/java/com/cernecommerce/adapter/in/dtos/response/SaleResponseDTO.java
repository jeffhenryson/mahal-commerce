package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class SaleResponseDTO {
    private Long id;
    private Long sessionId;
    private String warehouseCode;
    private Instant soldAt;
    private BigDecimal totalAmount;
    private List<SaleItemResponseDTO> items;
}
