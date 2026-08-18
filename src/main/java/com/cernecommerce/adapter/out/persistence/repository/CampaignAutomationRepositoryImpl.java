package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignAutomationEntity;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CampaignAutomationRepositoryImpl implements CampaignAutomationRepository {

    private static final Logger log = LoggerFactory.getLogger(CampaignAutomationRepositoryImpl.class);

    // Mapper estático — mesmo padrão de AuditLogRepositoryImpl: evita depender de um
    // ObjectMapper gerenciado pelo Spring, que pode faltar em contextos de teste leves.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CampaignAutomationJpaRepository campaignAutomationJpaRepository;

    public CampaignAutomationRepositoryImpl(CampaignAutomationJpaRepository campaignAutomationJpaRepository) {
        this.campaignAutomationJpaRepository = campaignAutomationJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CampaignAutomation> findById(Long id) {
        return campaignAutomationJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignAutomation> findAll() {
        return campaignAutomationJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public CampaignAutomation save(CampaignAutomation automation) {
        CampaignAutomationEntity entity = new CampaignAutomationEntity();
        entity.setId(automation.id());
        entity.setNome(automation.nome());
        entity.setGatilho(automation.gatilho());
        entity.setSegmentoAlvo(automation.segmentoAlvo());
        entity.setCanal(automation.canal());
        entity.setTemplate(automation.template());
        entity.setAtiva(automation.ativa());
        entity.setCriadoEm(automation.criadoEm());
        entity.setWebhookUrl(automation.webhookUrl());
        entity.setWebhookHeaders(serializeHeaders(automation.webhookHeaders()));
        CampaignAutomationEntity saved = campaignAutomationJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        campaignAutomationJpaRepository.deleteById(id);
    }

    private CampaignAutomation toDomain(CampaignAutomationEntity e) {
        return CampaignAutomation.of(e.getId(), e.getNome(), e.getGatilho(), e.getSegmentoAlvo(), e.getCanal(),
                e.getTemplate(), e.isAtiva(), e.getCriadoEm(), e.getWebhookUrl(),
                deserializeHeaders(e.getWebhookHeaders()));
    }

    private String serializeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(headers);
        } catch (JsonProcessingException e) {
            log.warn("crm.automation.webhookHeaders.serialize.failed", e);
            return null;
        }
    }

    private Map<String, String> deserializeHeaders(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("crm.automation.webhookHeaders.deserialize.failed", e);
            return Map.of();
        }
    }
}
