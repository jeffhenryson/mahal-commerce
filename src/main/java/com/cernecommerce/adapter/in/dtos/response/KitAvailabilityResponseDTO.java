package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class KitAvailabilityResponseDTO {
    private String sku;
    private String name;
    private BigDecimal buildableQuantity;
    private boolean blocked;
    private List<KitComponentAvailabilityResponseDTO> components;
}
