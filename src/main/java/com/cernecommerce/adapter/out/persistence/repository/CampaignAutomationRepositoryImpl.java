package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CampaignAutomationEntity;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CampaignAutomationRepositoryImpl implements CampaignAutomationRepository {

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
        CampaignAutomationEntity saved = campaignAutomationJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        campaignAutomationJpaRepository.deleteById(id);
    }

    private CampaignAutomation toDomain(CampaignAutomationEntity e) {
        return CampaignAutomation.of(e.getId(), e.getNome(), e.getGatilho(), e.getSegmentoAlvo(), e.getCanal(),
                e.getTemplate(), e.isAtiva(), e.getCriadoEm());
    }
}
