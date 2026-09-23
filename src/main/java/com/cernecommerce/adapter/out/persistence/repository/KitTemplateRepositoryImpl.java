package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.KitTemplateEntity;
import com.cernecommerce.adapter.out.persistence.entity.KitTemplateStepEntity;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.ports.out.estoque.KitTemplateRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional
public class KitTemplateRepositoryImpl implements KitTemplateRepository {

    private final KitTemplateJpaRepository jpaRepository;

    public KitTemplateRepositoryImpl(KitTemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public KitTemplate save(KitTemplate template) {
        KitTemplateEntity entity = template.id() == null ? new KitTemplateEntity()
                : jpaRepository.findByIdWithSteps(template.id()).orElseGet(KitTemplateEntity::new);
        entity.setName(template.name());
        entity.setDescription(template.description());
        entity.setImageUrl(template.imageUrl());
        entity.setDiscountPercent(template.discountPercent());
        entity.setActive(template.active());
        entity.setVisibleInPos(template.visibleInPos());
        entity.setVisibleInMarketplace(template.visibleInMarketplace());

        // Passo existente é ATUALIZADO no lugar, nunca recriado: o carrinho guarda kit_step_id e o
        // checkout recota por ele. Recriar os passos a cada edição do admin invalidaria todo kit
        // que estivesse num carrinho naquele momento.
        Set<Long> incomingIds = template.steps().stream().map(KitTemplateStep::id)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        entity.getSteps().removeIf(e -> !incomingIds.contains(e.getId()));
        Map<Long, KitTemplateStepEntity> persisted = entity.getSteps().stream()
                .collect(Collectors.toMap(KitTemplateStepEntity::getId, Function.identity()));
        for (KitTemplateStep step : template.steps()) {
            KitTemplateStepEntity stepEntity = step.id() == null ? null : persisted.get(step.id());
            if (stepEntity == null) {
                stepEntity = new KitTemplateStepEntity();
                stepEntity.setTemplate(entity);
                entity.getSteps().add(stepEntity);
            }
            stepEntity.setName(step.name());
            stepEntity.setDisplayOrder(step.displayOrder());
            stepEntity.setCategoryId(step.categoryId());
            stepEntity.setRequired(step.required());
            stepEntity.setMaxItems(step.maxItems());
        }
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitTemplate> findById(Long id) {
        return jpaRepository.findByIdWithSteps(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitTemplate> findAll() {
        return jpaRepository.findAllWithSteps().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<KitTemplate> findByNameIgnoreCase(String name) {
        return jpaRepository.findByNameIgnoringCase(name).map(this::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private KitTemplate toDomain(KitTemplateEntity e) {
        List<KitTemplateStep> steps = e.getSteps().stream()
                .map(s -> new KitTemplateStep(s.getId(), s.getName(), s.getDisplayOrder(), s.getCategoryId(),
                        s.isRequired(), s.getMaxItems()))
                .toList();
        return new KitTemplate(e.getId(), e.getName(), e.getDescription(), e.getImageUrl(), e.getDiscountPercent(),
                e.isActive(), e.isVisibleInPos(), e.isVisibleInMarketplace(), steps);
    }
}
