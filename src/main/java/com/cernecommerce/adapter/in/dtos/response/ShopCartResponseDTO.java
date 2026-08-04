package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class ShopCartResponseDTO {
    private List<ShopCartItemResponseDTO> items;
    private BigDecimal total;
    private Instant updatedAt;
}
