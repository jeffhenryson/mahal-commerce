package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductAttributeEmbeddable;
import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import com.cernecommerce.adapter.out.persistence.entity.ProductVariantEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
        entity.setActive(product.active());
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
    public PageResult<Product> findAll(int page, int size) {
        Page<Long> idPage = productJpaRepository.findAllIds(PageRequest.of(page, size));
        List<ProductEntity> entities = productJpaRepository.findAllByIdsWithVariants(idPage.getContent());
        List<Product> content = entities.stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    private Product toDomain(ProductEntity e) {
        List<ProductVariant> variants = e.getVariants().stream()
                .map(this::toDomain)
                .toList();
        return Product.of(e.getId(), e.getSku(), e.getName(), e.getCategory(), e.isActive(), variants);
    }

    private ProductVariant toDomain(ProductVariantEntity e) {
        List<ProductAttribute> attributes = e.getAttributes().stream()
                .map(a -> new ProductAttribute(a.getType(), a.getValue()))
                .toList();
        return ProductVariant.of(e.getId(), e.getSku(), attributes, e.isActive());
    }
}
