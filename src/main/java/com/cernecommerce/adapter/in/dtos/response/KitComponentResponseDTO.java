package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class KitComponentResponseDTO {
    private String componentSku;
    private BigDecimal quantity;

    /** Dados de catálogo do componente (Bloco 3.3) — {@code null} se o componente não existir mais. */
    private String componentName;
    private String componentImageUrl;
    private BigDecimal unitPrice;
    private boolean componentActive;
}
