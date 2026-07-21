package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.TagResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.TagSummaryResponseDTO;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;

public class TagDTOConverter {

    public TagResponseDTO toResponse(Tag tag) {
        TagResponseDTO dto = new TagResponseDTO();
        dto.setId(tag.id());
        dto.setNome(tag.nome());
        return dto;
    }

    public TagSummaryResponseDTO toResponse(TagSummary summary) {
        TagSummaryResponseDTO dto = new TagSummaryResponseDTO();
        dto.setId(summary.id());
        dto.setNome(summary.nome());
        dto.setClientesCount(summary.clientesCount());
        return dto;
    }
}
