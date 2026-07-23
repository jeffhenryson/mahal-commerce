package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaleRequest {
    @NotBlank
    private String warehouseCode;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;
}
