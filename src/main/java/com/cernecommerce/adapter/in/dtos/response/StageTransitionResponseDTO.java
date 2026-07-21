package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.CustomerStage;
import lombok.Data;

import java.time.Instant;

@Data
public class StageTransitionResponseDTO {
    private Long id;
    private Long customerId;
    private CustomerStage de;
    private CustomerStage para;
    private String autor;
    private Instant transicionadoEm;
}
