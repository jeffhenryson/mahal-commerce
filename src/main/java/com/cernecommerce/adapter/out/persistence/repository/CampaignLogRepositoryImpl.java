package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignLogEntryEntity;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class CampaignLogRepositoryImpl implements CampaignLogRepository {

    private final CampaignLogJpaRepository campaignLogJpaRepository;

    public CampaignLogRepositoryImpl(CampaignLogJpaRepository campaignLogJpaRepository) {
        this.campaignLogJpaRepository = campaignLogJpaRepository;
    }

    @Override
    public CampaignLogEntry save(CampaignLogEntry entry) {
        CampaignLogEntryEntity entity = new CampaignLogEntryEntity();
        entity.setId(entry.id());
        entity.setAutomationId(entry.automationId());
        entity.setCustomerId(entry.customerId());
        entity.setStatus(entry.status());
        entity.setDisparadoEm(entry.disparadoEm());
        entity.setConvertidoEm(entry.convertidoEm());
        entity.setErroDetalhe(entry.erroDetalhe());
        CampaignLogEntryEntity saved = campaignLogJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CampaignLogEntry> findByAutomationId(Long automationId) {
        return campaignLogJpaRepository.findByAutomationIdOrderByDisparadoEmDesc(automationId).stream()
                .map(this::toDomain)
                .toList();
    }

    private CampaignLogEntry toDomain(CampaignLogEntryEntity e) {
        return CampaignLogEntry.of(e.getId(), e.getAutomationId(), e.getCustomerId(), e.getStatus(),
                e.getDisparadoEm(), e.getConvertidoEm(), e.getErroDetalhe());
    }
}
