package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class GoodsReceiptResponseDTO {
    private Long id;
    private Long supplierId;
    private String warehouseCode;
    private String username;
    private Instant receivedAt;
    private List<GoodsReceiptItemResponseDTO> items;
}
