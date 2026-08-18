package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateCampaignAutomationRequest {
    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotNull
    private CampaignTrigger gatilho;

    @NotNull
    private CustomerStage segmentoAlvo;

    @NotNull
    private CampaignChannel canal;

    @NotBlank
    @Size(max = 2000)
    private String template;

    @Size(max = 500)
    private String webhookUrl;

    private Map<String, String> webhookHeaders;
}
