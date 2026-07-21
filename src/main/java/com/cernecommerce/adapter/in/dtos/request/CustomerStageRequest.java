package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.crm.CustomerStage;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomerStageRequest {
    @NotNull
    private CustomerStage estagio;
}
