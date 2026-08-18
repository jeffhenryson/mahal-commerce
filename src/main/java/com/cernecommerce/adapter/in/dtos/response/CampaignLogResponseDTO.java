package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import lombok.Data;

import java.time.Instant;

@Data
public class CampaignLogResponseDTO {
    private Long id;
    private Long automationId;
    private Long customerId;
    private CampaignDispatchStatus status;
    private Instant disparadoEm;
    private Instant convertidoEm;
    private String erroDetalhe;
}
