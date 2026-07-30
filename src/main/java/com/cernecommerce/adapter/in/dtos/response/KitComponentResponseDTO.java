package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KitComponentResponseDTO {
    private String componentSku;
    private BigDecimal quantity;
}
