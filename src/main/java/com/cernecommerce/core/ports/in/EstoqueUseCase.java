package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
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
     * Alteração parcial de produto (EST-F018): {@code name} e/ou {@code category} nulos são
     * mantidos como estão. Não altera {@code sku} (identidade referenciada como texto livre pelas
     * tabelas de estoque) nem as variações. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * não for um SKU pai existente.
     */
    Product updateProduct(String sku, String name, String category);

    /**
     * Ativa ou desativa um produto (EST-F018). Produto inativo <b>recusa entrada</b> de estoque
     * — manual ou por recebimento de Compras —, mas continua aceitando saída, para escoar o saldo
     * remanescente. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException} se o SKU
     * pai não existir.
     */
    Product setProductActive(String sku, boolean active);

    /**
     * Alteração parcial de depósito (EST-F018): {@code name} e/ou {@code type} nulos são mantidos.
     * Não altera {@code code}. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse updateWarehouse(String code, String name, WarehouseType type);

    /**
     * Ativa ou desativa um depósito (EST-F018). Mesma regra do produto: para de receber entrada,
     * continua despachando saída.
     */
    Warehouse setWarehouseActive(String code, boolean active);

    /**
     * Cria um depósito (loja física ou e-commerce). Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException}
     * se o código já existir.
     */
    Warehouse createWarehouse(String code, String name, WarehouseType type);

    /** Lista todos os depósitos cadastrados. */
    /** Lista depósitos paginados, ordenados por id. */
    PageResult<Warehouse> listWarehouses(int page, int size);

    /**
     * Busca um depósito por id. Existe para o adapter traduzir o {@code warehouseId} que os
     * modelos guardam no {@code warehouseCode} que a API expõe — caso do balanço de inventário,
     * consultado por id e sem código na requisição. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}.
     */
    Warehouse getWarehouse(Long warehouseId);

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

    // ---------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006)
    // ---------------------------------------------------------------------------------------

    /**
     * Abre um balanço para o depósito. Só pode haver <b>um aberto por depósito</b>: dois
     * simultâneos sobre o mesmo saldo se sobrescreveriam no fechamento. Lança
     * {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException} ou
     * {@link IllegalStateException} se já houver um aberto.
     */
    StockCount openStockCount(String warehouseCode, String username);

    /**
     * Registra o que foi contado de um SKU. É upsert por SKU — recontar sobrescreve. Exige
     * balanço {@link com.cernecommerce.core.domain.model.estoque.StockCountStatus#ABERTA} e SKU
     * existente no catálogo.
     */
    StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity);

    /**
     * Fecha o balanço e aplica os ajustes: para cada item cuja contagem <b>divirja</b> do saldo do
     * sistema, grava um {@link MovementType#AJUSTE} levando o saldo ao valor contado. Item que
     * bateu não gera movimentação — contagem certa não polui o ledger.
     *
     * <p>Tudo na mesma transação: se um SKU falhar, nenhum ajuste é aplicado e o balanço continua
     * aberto. Os itens ficam com {@code expectedQuantity} e {@code difference} carimbados, que é o
     * registro auditável da divergência.</p>
     */
    StockCount closeStockCount(Long stockCountId, String username);

    /** Abandona o balanço sem tocar em saldo nenhum. Exige que esteja aberto. */
    StockCount cancelStockCount(Long stockCountId);

    /** Consulta um balanço com seus itens. */
    StockCount getStockCount(Long stockCountId);

    /** Balanços de um depósito, dos mais recentes para os mais antigos. */
    PageResult<StockCount> listStockCounts(String warehouseCode, int page, int size);
}
