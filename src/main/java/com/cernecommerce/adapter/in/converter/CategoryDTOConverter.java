package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CategoryResponseDTO;
import com.cernecommerce.core.domain.model.estoque.Category;

public class CategoryDTOConverter {

    public CategoryResponseDTO toResponse(Category category) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(category.id());
        dto.setName(category.name());
        dto.setFeatured(category.featured());
        dto.setDisplayOrder(category.displayOrder());
        dto.setActive(category.active());
        return dto;
    }
}
