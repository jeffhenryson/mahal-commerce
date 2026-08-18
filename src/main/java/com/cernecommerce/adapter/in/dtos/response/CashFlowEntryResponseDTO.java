package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.financeiro.CashFlowCategory;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry.Direction;
import com.cernecommerce.core.domain.model.financeiro.CashFlowStatus;
import com.cernecommerce.core.domain.model.financeiro.LinkedEntityType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CashFlowEntryResponseDTO {
    private Long id;
    private LocalDate date;
    private String description;
    private String entityName;
    private CashFlowCategory category;
    private Direction direction;
    private BigDecimal amount;
    private CashFlowStatus status;
    private LocalDate dueDate;
    private LocalDate paymentDate;
    private LinkedEntityType linkedEntityType;
    private Long linkedEntityId;
}
