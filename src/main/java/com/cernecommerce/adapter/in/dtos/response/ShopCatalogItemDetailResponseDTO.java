package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ShopCatalogItemDetailResponseDTO {
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private boolean available;
    private List<ShopCatalogVariantResponseDTO> variants;
}
