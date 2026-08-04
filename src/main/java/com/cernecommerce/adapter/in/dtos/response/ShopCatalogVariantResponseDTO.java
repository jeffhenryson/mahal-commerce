package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class ShopCatalogVariantResponseDTO {
    private String sku;
    private List<ProductAttributeResponseDTO> attributes;
    private boolean available;
}
