package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import lombok.Data;

import java.time.Instant;

@Data
public class CampaignAutomationResponseDTO {
    private Long id;
    private String nome;
    private CampaignTrigger gatilho;
    private CustomerStage segmentoAlvo;
    private CampaignChannel canal;
    private String template;
    private boolean ativa;
    private Instant criadoEm;
}
