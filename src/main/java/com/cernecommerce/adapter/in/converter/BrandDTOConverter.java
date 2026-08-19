package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.BrandResponseDTO;
import com.cernecommerce.core.domain.model.estoque.Brand;

import java.math.BigDecimal;

public class BrandDTOConverter {

    public BrandResponseDTO toResponse(Brand brand) {
        return toResponse(brand, 0L, null);
    }

    public BrandResponseDTO toResponse(Brand brand, long productCount) {
        return toResponse(brand, productCount, null);
    }

    public BrandResponseDTO toResponse(Brand brand, long productCount, BigDecimal averageMarginPercent) {
        BrandResponseDTO dto = new BrandResponseDTO();
        dto.setId(brand.id());
        dto.setName(brand.name());
        dto.setActive(brand.active());
        dto.setProductCount(productCount);
        dto.setAverageMarginPercent(averageMarginPercent);
        return dto;
    }
}
