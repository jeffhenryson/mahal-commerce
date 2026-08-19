package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.StockBalanceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Optional;

public interface StockBalanceJpaRepository extends JpaRepository<StockBalanceEntity, Long> {

    Optional<StockBalanceEntity> findBySkuAndWarehouseId(String sku, Long warehouseId);

    Page<StockBalanceEntity> findByWarehouseIdOrderBySkuAsc(Long warehouseId, Pageable pageable);

    boolean existsBySku(String sku);

    // EST-F022 — GET /estoque/summary. `sb.sku` é texto livre sem FK: pode casar com o SKU pai
    // (join com `p`) ou com o de uma variação (join com `v`), nunca os dois — a checagem de
    // duplicidade em EstoqueService.createProduct garante que os dois espaços de nomes não
    // colidem. Precedência de custo efetivo (EST-F007): primeiro `sb.averageCost` (custo médio
    // ponderado do histórico real de entradas), senão o custo manual efetivo idêntico a
    // Product.effectivePricingFor — próprio da variação, senão o do pai dela, senão o do produto
    // direto —, senão zero (saldo sem nenhum custo conhecido contribui zero, não invalida a soma).
    //
    // `LEFT JOIN v.product vp` explícito, e NÃO navegação de caminho `v.product.costPrice` no
    // SELECT: esta última gera um INNER JOIN implícito na associação, que elimina do resultado
    // toda linha em que `v` é nulo (saldo cujo SKU é do produto pai, não de uma variação) — bug
    // silencioso que faz a soma inteira ignorar todo saldo que não é de variação.
    @Query("""
            SELECT COALESCE(SUM(sb.quantity * COALESCE(sb.averageCost, v.costPrice, vp.costPrice, p.costPrice, 0)), 0)
            FROM StockBalanceEntity sb
            LEFT JOIN ProductVariantEntity v ON v.sku = sb.sku
            LEFT JOIN v.product vp
            LEFT JOIN ProductEntity p ON p.sku = sb.sku
            """)
    BigDecimal sumInventoryValueAtCost();
}
