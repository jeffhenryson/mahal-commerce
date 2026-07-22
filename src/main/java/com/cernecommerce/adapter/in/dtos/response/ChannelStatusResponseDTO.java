package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.ChannelType;
import lombok.Data;

@Data
public class ChannelStatusResponseDTO {
    private ChannelType canal;
    private boolean conectado;
    private String provedor;
    private String detalhe;
}
