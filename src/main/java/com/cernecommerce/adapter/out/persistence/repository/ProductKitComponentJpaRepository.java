package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductKitComponentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductKitComponentJpaRepository extends JpaRepository<ProductKitComponentEntity, Long> {

    List<ProductKitComponentEntity> findByKitSku(String kitSku);

    void deleteByKitSku(String kitSku);

    /** Verdadeiro se o SKU já é componente de ALGUM kit — impede promovê-lo a KIT (EST-F015). */
    boolean existsByComponentSku(String componentSku);
}
