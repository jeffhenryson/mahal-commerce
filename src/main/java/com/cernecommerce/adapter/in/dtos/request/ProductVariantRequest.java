package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductVariantRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @Valid
    private List<ProductAttributeRequest> attributes;
}
