package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {

    /**
     * {@code sku}/{@code warehouseId} nulos não filtram por esse critério — alimenta tanto a
     * busca pontual (ambos informados) quanto o feed geral de movimentações (ambos omitidos).
     *
     * <p>O desempate por {@code id} não é cosmético: uma venda com N itens grava N movimentos no
     * mesmo loop e na mesma transação, com {@code created_at} idêntico. Ordenar só por
     * {@code created_at} deixa a chave de ordenação não-única, e aí a paginação fica instável —
     * a mesma linha pode voltar em duas páginas ou não aparecer em nenhuma. {@code id} é
     * BIGSERIAL monotônico, então dá ordem total e determinística.</p>
     */
    @Query("SELECT m FROM StockMovementEntity m "
            + "WHERE (:sku IS NULL OR m.sku = :sku) "
            + "AND (:warehouseId IS NULL OR m.warehouseId = :warehouseId) "
            + "ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovementEntity> search(@Param("sku") String sku, @Param("warehouseId") Long warehouseId,
            Pageable pageable);
}
