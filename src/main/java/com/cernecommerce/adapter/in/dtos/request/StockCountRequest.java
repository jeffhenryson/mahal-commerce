package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Abertura de um balanço de inventário (EST-F006). */
@Data
public class StockCountRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    private String warehouseCode;
}
