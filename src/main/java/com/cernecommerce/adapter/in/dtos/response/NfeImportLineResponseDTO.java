package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class NfeImportLineResponseDTO {

    private Long id;
    private int itemNumber;
    private String supplierProductCode;
    private String ean;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String lotCode;
    private LocalDate expiryDate;
    private String matchStatus;
    private String matchedSku;
}
