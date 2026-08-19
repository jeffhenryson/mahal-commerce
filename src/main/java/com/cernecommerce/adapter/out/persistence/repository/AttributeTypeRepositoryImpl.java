package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.AttributeTypeEntity;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.ports.out.estoque.AttributeTypeRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class AttributeTypeRepositoryImpl implements AttributeTypeRepository {

    private final AttributeTypeJpaRepository jpaRepository;

    public AttributeTypeRepositoryImpl(AttributeTypeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AttributeType save(AttributeType type) {
        AttributeTypeEntity entity = new AttributeTypeEntity();
        entity.setId(type.id());
        entity.setName(type.name());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AttributeType> findByName(String name) {
        return jpaRepository.findByNameIgnoringCase(name).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttributeType> findAllOrderByName() {
        return jpaRepository.findAllByOrderByNameAsc().stream().map(this::toDomain).toList();
    }

    private AttributeType toDomain(AttributeTypeEntity e) {
        return AttributeType.of(e.getId(), e.getName());
    }
}
