package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.ProductAttributeResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCatalogItemDetailResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCatalogItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCatalogVariantResponseDTO;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.ports.in.ShopUseCase.CatalogItem;
import com.cernecommerce.core.ports.in.ShopUseCase.CatalogItemDetail;
import com.cernecommerce.core.ports.in.ShopUseCase.CatalogVariant;

public class ShopCatalogDTOConverter {

    public ShopCatalogItemResponseDTO toResponse(CatalogItem item) {
        ShopCatalogItemResponseDTO dto = new ShopCatalogItemResponseDTO();
        dto.setSku(item.sku());
        dto.setName(item.name());
        dto.setCategory(item.category());
        dto.setPrice(item.price());
        dto.setAvailable(item.available());
        dto.setImageUrl(item.imageUrl());
        dto.setOnSale(item.onSale());
        return dto;
    }

    public ShopCatalogItemDetailResponseDTO toResponse(CatalogItemDetail detail) {
        ShopCatalogItemDetailResponseDTO dto = new ShopCatalogItemDetailResponseDTO();
        dto.setSku(detail.sku());
        dto.setName(detail.name());
        dto.setCategory(detail.category());
        dto.setPrice(detail.price());
        dto.setAvailable(detail.available());
        dto.setVariants(detail.variants().stream().map(this::toResponse).toList());
        dto.setImageUrl(detail.imageUrl());
        dto.setOnSale(detail.onSale());
        return dto;
    }

    private ShopCatalogVariantResponseDTO toResponse(CatalogVariant variant) {
        ShopCatalogVariantResponseDTO dto = new ShopCatalogVariantResponseDTO();
        dto.setSku(variant.sku());
        dto.setAttributes(variant.attributes().stream().map(this::toResponse).toList());
        dto.setAvailable(variant.available());
        return dto;
    }

    private ProductAttributeResponseDTO toResponse(ProductAttribute attribute) {
        ProductAttributeResponseDTO dto = new ProductAttributeResponseDTO();
        dto.setType(attribute.type());
        dto.setValue(attribute.value());
        return dto;
    }
}
