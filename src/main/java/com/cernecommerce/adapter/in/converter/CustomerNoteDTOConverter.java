package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CustomerNoteResponseDTO;
import com.cernecommerce.core.domain.model.crm.CustomerNote;

public class CustomerNoteDTOConverter {

    public CustomerNoteResponseDTO toResponse(CustomerNote note) {
        CustomerNoteResponseDTO dto = new CustomerNoteResponseDTO();
        dto.setId(note.id());
        dto.setCustomerId(note.customerId());
        dto.setAutor(note.autor());
        dto.setTexto(note.texto());
        dto.setCriadoEm(note.criadoEm());
        return dto;
    }
}
