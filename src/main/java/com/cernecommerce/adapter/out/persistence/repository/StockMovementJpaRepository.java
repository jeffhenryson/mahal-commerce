package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementJpaRepository extends JpaRepository<StockMovementEntity, Long> {

    /**
     * O desempate por {@code id} não é cosmético: uma venda com N itens grava N movimentos no
     * mesmo loop e na mesma transação, com {@code created_at} idêntico. Ordenar só por
     * {@code created_at} deixa a chave de ordenação não-única, e aí a paginação fica instável —
     * a mesma linha pode voltar em duas páginas ou não aparecer em nenhuma. {@code id} é
     * BIGSERIAL monotônico, então dá ordem total e determinística.
     */
    Page<StockMovementEntity> findBySkuAndWarehouseIdOrderByCreatedAtDescIdDesc(String sku, Long warehouseId,
            Pageable pageable);
}
