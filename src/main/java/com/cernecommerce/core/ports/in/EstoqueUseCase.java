package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do domínio <b>estoque</b>.
 */
public interface EstoqueUseCase {

    /**
     * Cria um produto (SKU pai) com suas variações. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException}
     * se o SKU do produto ou de alguma variação já existir.
     */
    Product createProduct(String sku, String name, String category, List<ProductVariant> variants);

    /** Lista produtos paginados. */
    PageResult<Product> listProducts(int page, int size);

    /**
     * Cria um depósito (loja física ou e-commerce). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException}
     * se o código já existir.
     */
    Warehouse createWarehouse(String code, String name, WarehouseType type);

    /** Lista todos os depósitos cadastrados. */
    List<Warehouse> listWarehouses();

    /**
     * Consulta o saldo de um SKU em um depósito. Retorna saldo zero se ainda não houve
     * nenhuma movimentação para o par SKU/depósito. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    StockBalance getStockBalance(String sku, String warehouseCode);

    /**
     * Registra uma movimentação manual de estoque (entrada, saída ou ajuste) e atualiza o
     * {@link StockBalance} correspondente na mesma transação. Retorna o saldo atualizado.
     * Lança {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o
     * SKU não existir no catálogo (nem como SKU pai, nem como SKU de variação),
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.InsufficientStockException} se
     * uma SAIDA deixaria o saldo negativo.
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username);

    /**
     * Histórico paginado de movimentações de um SKU em um depósito, mais recentes primeiro.
     * Retorna página vazia se o par SKU/depósito nunca foi movimentado. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir.
     */
    PageResult<StockMovement> listMovements(String sku, String warehouseCode, int page, int size);

    /**
     * Define (cria ou atualiza) o ponto de reposição de um SKU em um depósito. A partir dessa
     * chamada, toda movimentação que deixe o saldo abaixo de {@code minQuantity} dispara uma
     * notificação para os usuários com permissão {@code ESTOQUE_STOCK_MANAGE}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * não existir no catálogo, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} se o
     * código do depósito não existir.
     */
    void setReorderPoint(String sku, String warehouseCode, BigDecimal minQuantity);

    /**
     * Diagnóstico de integridade (EST-C011): pares SKU/depósito com saldo, movimentação ou ponto
     * de reposição gravados cujo SKU não existe no catálogo, nem como SKU pai nem como SKU de
     * variação.
     *
     * <p>Desde EST-C002 nenhuma escrita nova cria um órfão — {@code adjustStock} e
     * {@code setReorderPoint} barram SKU desconhecido. O que esta consulta levanta é o passivo
     * anterior àquela correção, que continua na base porque não há FK das tabelas de estoque
     * para {@code product}.</p>
     *
     * <p><b>Somente leitura, e de propósito:</b> o destino de cada órfão — cadastrar o produto
     * que falta ou expurgar a linha — é decisão humana. Expurgar em massa arriscaria apagar
     * histórico legítimo, então não existe operação de limpeza automática.</p>
     *
     * <p>Ordenado por {@code sku, warehouseCode}. Página vazia quando a base está íntegra.</p>
     */
    PageResult<OrphanSku> listOrphanSkus(int page, int size);
}
