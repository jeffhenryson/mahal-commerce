package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductAttributeEmbeddable;
import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import com.cernecommerce.adapter.out.persistence.entity.ProductVariantEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Transactional
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductJpaRepository productJpaRepository;

    public ProductRepositoryImpl(ProductJpaRepository productJpaRepository) {
        this.productJpaRepository = productJpaRepository;
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = new ProductEntity();
        entity.setId(product.id());
        entity.setSku(product.sku());
        entity.setName(product.name());
        entity.setCategory(product.category());
        entity.setBrand(product.brand());
        entity.setImageUrl(product.imageUrl());
        entity.setOnSale(product.onSale());
        entity.setSuperPromo(product.superPromo());
        entity.setDescription(product.description());
        entity.setVideoUrl(product.videoUrl());
        entity.setActive(product.active());
        entity.setCostPrice(product.pricing().costPrice());
        entity.setMarkupPercent(product.pricing().markupPercent());
        entity.setSalePrice(product.pricing().salePrice());
        entity.setOriginalPrice(product.pricing().originalPrice());
        entity.setType(product.type().name());
        entity.setLotTracked(product.lotTracked());
        entity.getImages().addAll(product.images());
        for (ProductVariant variant : product.variants()) {
            ProductVariantEntity variantEntity = new ProductVariantEntity();
            variantEntity.setId(variant.id());
            variantEntity.setProduct(entity);
            variantEntity.setSku(variant.sku());
            variantEntity.setActive(variant.active());
            variantEntity.getAttributes().addAll(variant.attributes().stream()
                    .map(a -> new ProductAttributeEmbeddable(a.type(), a.value()))
                    .toList());
            entity.getVariants().add(variantEntity);
        }
        ProductEntity saved = productJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        return productJpaRepository.findBySku(sku).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> findByAnySku(String sku) {
        return productJpaRepository.findByAnySku(sku).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySku(String sku) {
        return productJpaRepository.existsBySkuOrVariantSku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSkuActive(String sku) {
        return productJpaRepository.isSkuActive(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findAll(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction) {
        Pageable pageable = PageRequest.of(page, size, toSort(sortField, direction));
        Page<Long> idPage = productJpaRepository.findFilteredIds(
                likePattern(filter.search()), filter.category(), filter.brand(), filter.active(), pageable);
        return toPageResult(idPage, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale) {
        Page<Long> idPage = productJpaRepository.findActivePricedIds(PageRequest.of(page, size), onSale);
        return toPageResult(idPage, page, size);
    }

    /**
     * Segunda metade do padrão ID-first: resolve as entidades dos ids já paginados.
     *
     * <p>A ordem é reimposta a partir da página de ids em vez de vir da consulta de fetch, que tem
     * {@code ORDER BY p.id} fixo — sem isso, pedir ordenação por nome ou preço devolveria a
     * página certa de produtos na ordem errada, que é o tipo de bug que passa despercebido
     * porque o conteúdo está correto.</p>
     */
    private PageResult<Product> toPageResult(Page<Long> idPage, int page, int size) {
        List<Long> ids = idPage.getContent();
        Map<Long, ProductEntity> byId = productJpaRepository.findAllByIdsWithVariants(ids).stream()
                .collect(Collectors.toMap(ProductEntity::getId, e -> e));
        List<Product> content = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(this::toDomain)
                .toList();
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    /**
     * Traduz o campo do domínio para o atributo JPA. O desempate por {@code id} é sempre
     * acrescentado: nome e preço não são únicos, e paginar por chave não-única faz o banco
     * devolver a mesma linha em duas páginas ou em nenhuma (EST-C012).
     */
    private static Sort toSort(ProductSortField sortField, SortDirection direction) {
        Sort.Direction jpaDirection = direction == SortDirection.DESC
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        String property = switch (sortField) {
            case ID -> "id";
            case NAME -> "name";
            case SALE_PRICE -> "salePrice";
        };
        Sort sort = Sort.by(jpaDirection, property);
        return sortField == ProductSortField.ID ? sort : sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    /** Busca parcial em nome ou SKU. O termo já chega em minúsculas de {@link ProductFilter}. */
    private static String likePattern(String search) {
        return search == null ? null : "%" + search + "%";
    }

    private Product toDomain(ProductEntity e) {
        List<ProductVariant> variants = e.getVariants().stream()
                .map(this::toDomain)
                .toList();
        Pricing pricing = Pricing.of(e.getCostPrice(), e.getMarkupPercent(), e.getSalePrice(), e.getOriginalPrice());
        ProductType type = ProductType.valueOf(e.getType());
        return Product.of(e.getId(), e.getSku(), e.getName(), e.getCategory(), e.isActive(), variants, pricing, type,
                e.isLotTracked(), e.getBrand(), e.getImageUrl(), e.isOnSale(), e.isSuperPromo(), e.getDescription(),
                e.getVideoUrl(), List.copyOf(e.getImages()));
    }

    private ProductVariant toDomain(ProductVariantEntity e) {
        List<ProductAttribute> attributes = e.getAttributes().stream()
                .map(a -> new ProductAttribute(a.getType(), a.getValue()))
                .toList();
        return ProductVariant.of(e.getId(), e.getSku(), attributes, e.isActive());
    }
}
