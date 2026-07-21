package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StageTransitionEntity;
import com.cernecommerce.core.domain.model.crm.StageTransition;
import com.cernecommerce.core.ports.out.crm.StageTransitionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class StageTransitionRepositoryImpl implements StageTransitionRepository {

    private final StageTransitionJpaRepository stageTransitionJpaRepository;

    public StageTransitionRepositoryImpl(StageTransitionJpaRepository stageTransitionJpaRepository) {
        this.stageTransitionJpaRepository = stageTransitionJpaRepository;
    }

    @Override
    public StageTransition save(StageTransition transition) {
        StageTransitionEntity entity = new StageTransitionEntity();
        entity.setId(transition.id());
        entity.setCustomerId(transition.customerId());
        entity.setDe(transition.de());
        entity.setPara(transition.para());
        entity.setAutor(transition.autor());
        entity.setTransicionadoEm(transition.transicionadoEm());
        StageTransitionEntity saved = stageTransitionJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StageTransition> findByCustomerId(Long customerId) {
        return stageTransitionJpaRepository.findByCustomerIdOrderByTransicionadoEmDesc(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    private StageTransition toDomain(StageTransitionEntity e) {
        return StageTransition.of(e.getId(), e.getCustomerId(), e.getDe(), e.getPara(), e.getAutor(),
                e.getTransicionadoEm());
    }
}
