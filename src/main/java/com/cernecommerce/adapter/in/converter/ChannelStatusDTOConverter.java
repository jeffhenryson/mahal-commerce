package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.ChannelStatusResponseDTO;
import com.cernecommerce.core.domain.model.crm.ChannelStatus;

public class ChannelStatusDTOConverter {

    public ChannelStatusResponseDTO toResponse(ChannelStatus status) {
        ChannelStatusResponseDTO dto = new ChannelStatusResponseDTO();
        dto.setCanal(status.canal());
        dto.setConectado(status.conectado());
        dto.setProvedor(status.provedor());
        dto.setDetalhe(status.detalhe());
        return dto;
    }
}
