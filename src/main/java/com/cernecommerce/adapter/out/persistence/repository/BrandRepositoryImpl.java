package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductBrandEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.ports.out.estoque.BrandRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class BrandRepositoryImpl implements BrandRepository {

    private final ProductBrandJpaRepository jpaRepository;

    public BrandRepositoryImpl(ProductBrandJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Brand save(Brand brand) {
        ProductBrandEntity entity = new ProductBrandEntity();
        entity.setId(brand.id());
        entity.setName(brand.name());
        entity.setActive(brand.active());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Brand> findByName(String name) {
        return jpaRepository.findByNameIgnoringCase(name).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Brand> findAll(int page, int size) {
        Page<ProductBrandEntity> result = jpaRepository.findAllByOrderByNameAsc(PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Brand> findByNameContaining(String search, int page, int size) {
        String normalized = normalizeSearch(search);
        Page<ProductBrandEntity> result = jpaRepository.search(
                normalized == null ? null : "%" + normalized + "%", PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Brand> findActiveOrdered() {
        return jpaRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private static String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private Brand toDomain(ProductBrandEntity e) {
        return Brand.of(e.getId(), e.getName(), e.isActive());
    }
}
