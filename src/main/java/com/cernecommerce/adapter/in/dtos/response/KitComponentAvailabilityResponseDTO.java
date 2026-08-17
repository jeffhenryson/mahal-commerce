package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KitComponentAvailabilityResponseDTO {
    private String componentSku;
    private BigDecimal quantity;
    private BigDecimal availableQuantity;
    private BigDecimal buildableQuantity;
}
