package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockCountEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface StockCountJpaRepository extends JpaRepository<StockCountEntity, Long> {

    @Query("SELECT c FROM StockCountEntity c LEFT JOIN FETCH c.items WHERE c.id = :id")
    Optional<StockCountEntity> findByIdWithItems(Long id);

    Optional<StockCountEntity> findByWarehouseIdAndStatus(Long warehouseId, String status);

    // Padrão ID-first, como na listagem de produtos: paginar os ids e só depois fazer JOIN FETCH
    // dos itens, para não misturar LIMIT/OFFSET com fetch de coleção.
    @Query("SELECT c.id FROM StockCountEntity c WHERE c.warehouseId = :warehouseId "
            + "ORDER BY c.createdAt DESC, c.id DESC")
    Page<Long> findIdsByWarehouseId(Long warehouseId, Pageable pageable);

    @Query("SELECT DISTINCT c FROM StockCountEntity c LEFT JOIN FETCH c.items "
            + "WHERE c.id IN :ids ORDER BY c.createdAt DESC, c.id DESC")
    List<StockCountEntity> findAllByIdsWithItems(List<Long> ids);
}
