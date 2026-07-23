package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
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
     * Lança {@link com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException}
     * se o código do depósito não existir, ou
     * {@link com.cernecommerce.core.domain.exception.estoque.InsufficientStockException} se
     * uma SAIDA deixaria o saldo negativo.
     */
    StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username);
}
