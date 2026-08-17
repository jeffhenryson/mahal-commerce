package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductKitComponentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductKitComponentJpaRepository extends JpaRepository<ProductKitComponentEntity, Long> {

    List<ProductKitComponentEntity> findByKitSku(String kitSku);

    void deleteByKitSku(String kitSku);

    /** Verdadeiro se o SKU já é componente de ALGUM kit — impede promovê-lo a KIT (EST-F015). */
    boolean existsByComponentSku(String componentSku);

    /**
     * Kits que usam {@code componentSku} na receita — usado para detectar quando uma
     * baixa/reserva torna algum kit sem estoque suficiente para ser montado (Bloco 1.2).
     */
    @Query("SELECT DISTINCT e.kitSku FROM ProductKitComponentEntity e WHERE e.componentSku = :componentSku")
    List<String> findKitSkusByComponentSku(@Param("componentSku") String componentSku);
}
