package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public class EstoqueService implements EstoqueUseCase {

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;

    public EstoqueService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    @Override
    @Transactional
    public Product createProduct(String sku, String name, String category, List<ProductVariant> variants) {
        productRepository.findBySku(sku).ifPresent(p -> {
            throw new DuplicateSkuException(sku);
        });
        Product product = Product.create(sku, name, category, variants);
        return productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> listProducts(int page, int size) {
        return productRepository.findAll(page, size);
    }

    @Override
    @Transactional
    public Warehouse createWarehouse(String code, String name, WarehouseType type) {
        warehouseRepository.findByCode(code).ifPresent(w -> {
            throw new DuplicateWarehouseCodeException(code);
        });
        return warehouseRepository.save(Warehouse.create(code, name, type));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Warehouse> listWarehouses() {
        return warehouseRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public StockBalance getStockBalance(String sku, String warehouseCode) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        return stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
    }

    @Override
    @Transactional
    public StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        StockBalance updated = current.apply(type, quantity);
        stockMovementRepository.save(StockMovement.create(sku, warehouse.id(), type, quantity, reason, username));
        return stockBalanceRepository.save(updated);
    }
}
