package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class ProductVariantResponseDTO {
    private Long id;
    private String sku;
    private boolean active;
    private List<ProductAttributeResponseDTO> attributes;
}
