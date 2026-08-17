package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductKitComponentEntity;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.ports.out.estoque.KitComponentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class KitComponentRepositoryImpl implements KitComponentRepository {

    private final ProductKitComponentJpaRepository jpaRepository;

    public KitComponentRepositoryImpl(ProductKitComponentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitComponent> findByKitSku(String kitSku) {
        return jpaRepository.findByKitSku(kitSku).stream().map(this::toDomain).toList();
    }

    @Override
    public void replaceRecipe(String kitSku, List<KitComponent> components) {
        jpaRepository.deleteByKitSku(kitSku);
        jpaRepository.flush();
        jpaRepository.saveAll(components.stream().map(this::toEntity).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsedAsComponent(String sku) {
        return jpaRepository.existsByComponentSku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findKitSkusByComponentSku(String componentSku) {
        return jpaRepository.findKitSkusByComponentSku(componentSku);
    }

    private KitComponent toDomain(ProductKitComponentEntity e) {
        return KitComponent.of(e.getId(), e.getKitSku(), e.getComponentSku(), e.getQuantity());
    }

    private ProductKitComponentEntity toEntity(KitComponent component) {
        return new ProductKitComponentEntity(component.id(), component.kitSku(), component.componentSku(),
                component.quantity());
    }
}
