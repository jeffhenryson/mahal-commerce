package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.StageTransitionResponseDTO;
import com.cernecommerce.core.domain.model.crm.StageTransition;

public class StageTransitionDTOConverter {

    public StageTransitionResponseDTO toResponse(StageTransition transition) {
        StageTransitionResponseDTO dto = new StageTransitionResponseDTO();
        dto.setId(transition.id());
        dto.setCustomerId(transition.customerId());
        dto.setDe(transition.de());
        dto.setPara(transition.para());
        dto.setAutor(transition.autor());
        dto.setTransicionadoEm(transition.transicionadoEm());
        return dto;
    }
}
