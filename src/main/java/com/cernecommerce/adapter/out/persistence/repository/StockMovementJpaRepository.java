package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

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
            + "AND (:type IS NULL OR m.type = :type) "
            + "AND (:from IS NULL OR m.createdAt >= :from) "
            + "AND (:to IS NULL OR m.createdAt <= :to) "
            + "ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovementEntity> search(@Param("sku") String sku, @Param("warehouseId") Long warehouseId,
            @Param("type") String type, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * Últimas ENTRADAs de um SKU num depósito (item 2 — histórico de compras). Mesmo desempate
     * por {@code id} de {@link #search} — várias entradas do mesmo recebimento podem gravar
     * {@code createdAt} idêntico.
     */
    @Query("SELECT m FROM StockMovementEntity m "
            + "WHERE m.sku = :sku AND m.warehouseId = :warehouseId AND m.type = 'ENTRADA' "
            + "ORDER BY m.createdAt DESC, m.id DESC")
    Page<StockMovementEntity> searchEntradas(@Param("sku") String sku, @Param("warehouseId") Long warehouseId,
            Pageable pageable);

    boolean existsBySku(String sku);
}
