package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CampaignAutomationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CampaignLogResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.WebhookTestResultResponseDTO;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;

public class CampaignDTOConverter {

    public CampaignAutomationResponseDTO toResponse(CampaignAutomation automation) {
        CampaignAutomationResponseDTO dto = new CampaignAutomationResponseDTO();
        dto.setId(automation.id());
        dto.setNome(automation.nome());
        dto.setGatilho(automation.gatilho());
        dto.setSegmentoAlvo(automation.segmentoAlvo());
        dto.setCanal(automation.canal());
        dto.setTemplate(automation.template());
        dto.setAtiva(automation.ativa());
        dto.setCriadoEm(automation.criadoEm());
        dto.setWebhookUrl(automation.webhookUrl());
        return dto;
    }

    public CampaignLogResponseDTO toResponse(CampaignLogEntry entry) {
        CampaignLogResponseDTO dto = new CampaignLogResponseDTO();
        dto.setId(entry.id());
        dto.setAutomationId(entry.automationId());
        dto.setCustomerId(entry.customerId());
        dto.setStatus(entry.status());
        dto.setDisparadoEm(entry.disparadoEm());
        dto.setConvertidoEm(entry.convertidoEm());
        dto.setErroDetalhe(entry.erroDetalhe());
        return dto;
    }

    public WebhookTestResultResponseDTO toResponse(WebhookTestResult result) {
        WebhookTestResultResponseDTO dto = new WebhookTestResultResponseDTO();
        dto.setSuccess(result.success());
        dto.setStatusCode(result.statusCode());
        dto.setErrorMessage(result.errorMessage());
        dto.setPayloadEnviado(result.payloadEnviado());
        return dto;
    }
}
