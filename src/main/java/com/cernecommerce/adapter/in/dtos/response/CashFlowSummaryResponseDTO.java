package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashFlowSummaryResponseDTO {
    private BigDecimal totalInflow;
    private BigDecimal totalOutflow;
    private BigDecimal balance;
    private long entryCount;
}
