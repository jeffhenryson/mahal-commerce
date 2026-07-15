package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.ProductAttributeRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductVariantRequest;
import com.cernecommerce.adapter.in.dtos.response.ProductAttributeResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductVariantResponseDTO;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;

import java.util.List;

public class ProductDTOConverter {

    public List<ProductVariant> toVariants(List<ProductVariantRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> ProductVariant.create(r.getSku(), toAttributes(r.getAttributes())))
                .toList();
    }

    private List<ProductAttribute> toAttributes(List<ProductAttributeRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> new ProductAttribute(r.getType(), r.getValue()))
                .toList();
    }

    public ProductResponseDTO toResponse(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.id());
        dto.setSku(product.sku());
        dto.setName(product.name());
        dto.setCategory(product.category());
        dto.setActive(product.active());
        dto.setVariants(product.variants().stream().map(this::toResponse).toList());
        return dto;
    }

    private ProductVariantResponseDTO toResponse(ProductVariant variant) {
        ProductVariantResponseDTO dto = new ProductVariantResponseDTO();
        dto.setId(variant.id());
        dto.setSku(variant.sku());
        dto.setActive(variant.active());
        dto.setAttributes(variant.attributes().stream().map(this::toResponse).toList());
        return dto;
    }

    private ProductAttributeResponseDTO toResponse(ProductAttribute attribute) {
        ProductAttributeResponseDTO dto = new ProductAttributeResponseDTO();
        dto.setType(attribute.type());
        dto.setValue(attribute.value());
        return dto;
    }
}
