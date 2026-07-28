package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockReservationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockReservationJpaRepository extends JpaRepository<StockReservationEntity, Long> {

    List<StockReservationEntity> findByOwnerReferenceAndStatus(String ownerReference, String status);

    /**
     * Filtros opcionais combinados: cada parâmetro nulo neutraliza a própria cláusula.
     *
     * <p>O desempate por {@code id} segue a mesma razão de {@code StockMovementJpaRepository}: um
     * checkout de N itens grava N reservas na mesma transação, com {@code created_at} idêntico.
     * Sem chave de ordenação única a paginação fica instável.</p>
     */
    @Query("""
            SELECT r FROM StockReservationEntity r
            WHERE (:sku IS NULL OR r.sku = :sku)
              AND (:warehouseId IS NULL OR r.warehouseId = :warehouseId)
              AND (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    Page<StockReservationEntity> findByFilters(@Param("sku") String sku,
            @Param("warehouseId") Long warehouseId,
            @Param("status") String status,
            Pageable pageable);

    /**
     * Reservas ativas já vencidas, mais antigas primeiro — a que venceu há mais tempo é a que está
     * segurando saldo indevidamente há mais tempo.
     */
    @Query("""
            SELECT r FROM StockReservationEntity r
            WHERE r.status = 'ACTIVE' AND r.expiresAt <= :now
            ORDER BY r.expiresAt ASC, r.id ASC
            """)
    List<StockReservationEntity> findExpired(@Param("now") Instant now, Pageable pageable);
}
