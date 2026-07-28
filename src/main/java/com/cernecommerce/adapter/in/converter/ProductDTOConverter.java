package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.PricingRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductAttributeRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductVariantRequest;
import com.cernecommerce.adapter.in.dtos.response.PricingResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductAttributeResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductVariantResponseDTO;
import com.cernecommerce.core.domain.model.estoque.Pricing;
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

    /**
     * Converte o bloco de precificação do request (EST-F019). Retorna {@code null} quando o
     * bloco não veio — e é justamente esse {@code null} que o {@code updateProduct} lê como
     * "não mexer no preço".
     */
    public Pricing toPricing(PricingRequest request) {
        if (request == null) {
            return null;
        }
        return Pricing.of(request.getCostPrice(), request.getMarkupPercent(), request.getSalePrice());
    }

    public ProductResponseDTO toResponse(Product product) {
        ProductResponseDTO dto = new ProductResponseDTO();
        dto.setId(product.id());
        dto.setSku(product.sku());
        dto.setName(product.name());
        dto.setCategory(product.category());
        dto.setActive(product.active());
        dto.setVariants(product.variants().stream().map(this::toResponse).toList());
        dto.setPricing(toResponse(product.pricing()));
        return dto;
    }

    /** Serializa a precificação com os derivados já calculados pelo domínio. */
    public PricingResponseDTO toResponse(Pricing pricing) {
        PricingResponseDTO dto = new PricingResponseDTO();
        dto.setCostPrice(pricing.costPrice());
        dto.setMarkupPercent(pricing.markupPercent());
        dto.setSalePrice(pricing.salePrice());
        dto.setSuggestedPrice(pricing.suggestedPrice());
        dto.setEffectivePrice(pricing.effectivePrice());
        dto.setMarginAmount(pricing.marginAmount());
        dto.setMarginPercent(pricing.marginPercent());
        dto.setEffectiveMarkupPercent(pricing.effectiveMarkupPercent());
        dto.setPriced(pricing.isPriced());
        dto.setBelowCost(pricing.isBelowCost());
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
