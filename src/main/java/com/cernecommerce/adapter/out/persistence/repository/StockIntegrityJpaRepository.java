package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockMovementEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Diagnóstico de integridade referencial do estoque (EST-C011).
 *
 * <p>Estende {@link Repository} apenas como marcador — não há entidade para "SKU órfão", e
 * {@link StockMovementEntity} entra só para satisfazer o tipo de domínio exigido pelo Spring
 * Data. Nenhum método CRUD é exposto.</p>
 *
 * <p><b>Por que query nativa:</b> a origem das linhas é a união de três tabelas, e JPQL não tem
 * {@code UNION}. Precedente de query nativa no projeto:
 * {@code NotificationPreferenceJpaRepository.upsert}.</p>
 */
public interface StockIntegrityJpaRepository extends Repository<StockMovementEntity, Long> {

    /**
     * Pares (sku, depósito) presentes em {@code stock_balance}, {@code stock_movement} ou
     * {@code stock_reorder_point} cujo SKU não existe no catálogo.
     *
     * <p>As colunas voltam por posição — ver {@code StockIntegrityRepositoryImpl.toDomain}:
     * {@code 0} sku, {@code 1} warehouse_code, {@code 2} quantity, {@code 3} movement_count,
     * {@code 4} has_reorder_point, {@code 5} last_movement_at. Mapear por índice em vez de por
     * projeção de interface evita depender de como cada banco dobra a caixa dos aliases
     * (o perfil {@code dev} roda H2 com {@code DATABASE_TO_LOWER=TRUE}).</p>
     *
     * <p>O anti-join com {@code NOT EXISTS} contra as duas tabelas de catálogo espelha
     * {@code ProductJpaRepository.existsBySkuOrVariantSku}: SKU pai e SKU de variação
     * compartilham o mesmo espaço de nomes, então ambos precisam ser considerados conhecidos.</p>
     *
     * <p>A ordenação é por {@code (sku, warehouse_code)} — chave única, porque chave de
     * ordenação não-única deixa a paginação instável (lição do EST-C012).</p>
     */
    @Query(value = """
            SELECT o.sku,
                   w.code,
                   COALESCE(b.quantity, 0),
                   (SELECT COUNT(*) FROM stock_movement m
                     WHERE m.sku = o.sku AND m.warehouse_id = o.warehouse_id),
                   CASE WHEN r.id IS NULL THEN FALSE ELSE TRUE END,
                   (SELECT MAX(m.created_at) FROM stock_movement m
                     WHERE m.sku = o.sku AND m.warehouse_id = o.warehouse_id)
            FROM (SELECT sku, warehouse_id FROM stock_balance
                  UNION
                  SELECT sku, warehouse_id FROM stock_movement
                  UNION
                  SELECT sku, warehouse_id FROM stock_reorder_point) o
            JOIN warehouse w ON w.id = o.warehouse_id
            LEFT JOIN stock_balance b ON b.sku = o.sku AND b.warehouse_id = o.warehouse_id
            LEFT JOIN stock_reorder_point r ON r.sku = o.sku AND r.warehouse_id = o.warehouse_id
            WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.sku = o.sku)
              AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = o.sku)
            ORDER BY o.sku, w.code
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM (SELECT sku, warehouse_id FROM stock_balance
                  UNION
                  SELECT sku, warehouse_id FROM stock_movement
                  UNION
                  SELECT sku, warehouse_id FROM stock_reorder_point) o
            JOIN warehouse w ON w.id = o.warehouse_id
            WHERE NOT EXISTS (SELECT 1 FROM product p WHERE p.sku = o.sku)
              AND NOT EXISTS (SELECT 1 FROM product_variant v WHERE v.sku = o.sku)
            """,
            nativeQuery = true)
    Page<Object[]> findOrphanSkus(Pageable pageable);
}
