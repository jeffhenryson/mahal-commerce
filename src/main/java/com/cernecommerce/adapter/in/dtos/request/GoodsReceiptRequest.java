package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GoodsReceiptRequest {
    @NotNull
    private Long supplierId;

    @NotBlank
    private String warehouseCode;

    @NotEmpty
    @Valid
    private List<GoodsReceiptItemRequest> items;
}
