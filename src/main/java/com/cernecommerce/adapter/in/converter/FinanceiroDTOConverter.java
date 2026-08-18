package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CashFlowEntryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashFlowSummaryResponseDTO;
import com.cernecommerce.core.domain.model.financeiro.CashFlowEntry;
import com.cernecommerce.core.domain.model.financeiro.CashFlowSummary;
import org.springframework.stereotype.Component;

@Component
public class FinanceiroDTOConverter {

    public CashFlowEntryResponseDTO toResponse(CashFlowEntry entry) {
        CashFlowEntryResponseDTO dto = new CashFlowEntryResponseDTO();
        dto.setId(entry.id());
        dto.setDate(entry.date());
        dto.setDescription(entry.description());
        dto.setEntityName(entry.entityName());
        dto.setCategory(entry.category());
        dto.setDirection(entry.direction());
        dto.setAmount(entry.amount());
        dto.setStatus(entry.status());
        dto.setDueDate(entry.dueDate());
        dto.setPaymentDate(entry.paymentDate());
        dto.setLinkedEntityType(entry.linkedEntityType());
        dto.setLinkedEntityId(entry.linkedEntityId());
        return dto;
    }

    public CashFlowSummaryResponseDTO toResponse(CashFlowSummary summary) {
        CashFlowSummaryResponseDTO dto = new CashFlowSummaryResponseDTO();
        dto.setTotalInflow(summary.totalInflow());
        dto.setTotalOutflow(summary.totalOutflow());
        dto.setBalance(summary.balance());
        dto.setEntryCount(summary.entryCount());
        return dto;
    }
}
