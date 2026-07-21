package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.TagEntity;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.TagSummary;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class TagRepositoryImpl implements TagRepository {

    private final TagJpaRepository tagJpaRepository;

    public TagRepositoryImpl(TagJpaRepository tagJpaRepository) {
        this.tagJpaRepository = tagJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tag> findById(Long id) {
        return tagJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tag> findByNome(String nome) {
        return tagJpaRepository.findByNome(nome).map(this::toDomain);
    }

    @Override
    public Tag save(Tag tag) {
        TagEntity entity = new TagEntity();
        entity.setId(tag.id());
        entity.setNome(tag.nome());
        TagEntity saved = tagJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        tagJpaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagSummary> findAllWithCustomerCount() {
        return tagJpaRepository.findAllWithCustomerCount().stream()
                .map(row -> new TagSummary((Long) row[0], (String) row[1], (Long) row[2]))
                .toList();
    }

    private Tag toDomain(TagEntity e) {
        return Tag.of(e.getId(), e.getNome());
    }
}
