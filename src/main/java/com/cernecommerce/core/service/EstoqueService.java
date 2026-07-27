package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderAlert;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EstoqueService implements EstoqueUseCase {

    private static final String STOCK_MANAGE_PERMISSION = "ESTOQUE_STOCK_MANAGE";
    private static final String REORDER_ALERT_BATCH = "estoque.reorder-alerts";

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReorderPointRepository reorderPointRepository;
    private final StockIntegrityRepository stockIntegrityRepository;
    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final AfterCommitExecutor afterCommitExecutor;

    public EstoqueService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository,
            ReorderPointRepository reorderPointRepository, StockIntegrityRepository stockIntegrityRepository,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            AfterCommitExecutor afterCommitExecutor) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.reorderPointRepository = reorderPointRepository;
        this.stockIntegrityRepository = stockIntegrityRepository;
        this.notificationUseCase = notificationUseCase;
        this.userRepository = userRepository;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    @Override
    @Transactional
    public Product createProduct(String sku, String name, String category, List<ProductVariant> variants) {
        List<ProductVariant> safeVariants = variants == null ? List.of() : variants;
        // O SKU pai e os das variações compartilham o mesmo espaço de nomes: uk_product_sku e
        // uk_product_variant_sku. Checar os dois aqui evita que a violação de constraint escape
        // como DataIntegrityViolationException, que o contrato do use case não prevê.
        Set<String> candidates = new LinkedHashSet<>();
        candidates.add(sku);
        for (ProductVariant variant : safeVariants) {
            if (!candidates.add(variant.sku())) {
                throw new DuplicateSkuException(variant.sku());
            }
        }
        for (String candidate : candidates) {
            if (productRepository.existsBySku(candidate)) {
                throw new DuplicateSkuException(candidate);
            }
        }
        Product product = Product.create(sku, name, category, safeVariants);
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
        requireKnownSku(sku);
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        StockBalance updated = current.apply(type, quantity);
        stockMovementRepository.save(StockMovement.create(sku, warehouse.id(), type, quantity, reason, username));
        StockBalance saved = stockBalanceRepository.save(updated);
        notifyIfBelowReorderPoint(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockMovement> listMovements(String sku, String warehouseCode, int page, int size) {
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        return stockMovementRepository.findBySkuAndWarehouseId(sku, warehouse.id(), page, size);
    }

    @Override
    @Transactional
    public void setReorderPoint(String sku, String warehouseCode, BigDecimal minQuantity) {
        requireKnownSku(sku);
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        Long existingId = reorderPointRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .map(ReorderPoint::id)
                .orElse(null);
        reorderPointRepository.save(new ReorderPoint(existingId, sku, warehouse.id(), minQuantity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrphanSku> listOrphanSkus(int page, int size) {
        return stockIntegrityRepository.findOrphanSkus(page, size);
    }

    /**
     * Barra SKU que não existe no catálogo antes de qualquer escrita. Sem isso — e não há FK de
     * {@code stock_balance}/{@code stock_movement} para {@code product} — um SKU digitado errado
     * vindo do PDV ou de Compras cria saldo e ledger órfãos silenciosamente.
     */
    private void requireKnownSku(String sku) {
        if (!productRepository.existsBySku(sku)) {
            throw new ProductNotFoundException(sku);
        }
    }

    /**
     * Acumula o alerta em vez de enviá-lo na hora. Uma venda que derruba N SKUs abaixo do mínimo
     * registra N alertas, mas gera <b>uma</b> notificação por destinatário, despachada depois do
     * commit — nem a transação de venda espera o envio, nem alguém é avisado sobre uma venda que
     * acabou revertida.
     */
    private void notifyIfBelowReorderPoint(StockBalance balance) {
        reorderPointRepository.findBySkuAndWarehouseId(balance.sku(), balance.warehouseId())
                .filter(reorderPoint -> reorderPoint.isBelow(balance.quantity()))
                .ifPresent(reorderPoint -> afterCommitExecutor.accumulate(REORDER_ALERT_BATCH,
                        new ReorderAlert(balance.sku(), balance.quantity(), reorderPoint.minQuantity()),
                        this::dispatchReorderAlerts));
    }

    private void dispatchReorderAlerts(List<ReorderAlert> alerts) {
        // O mesmo SKU pode aparecer mais de uma vez na operação (dois itens do mesmo produto na
        // venda); vale o último saldo observado.
        Map<String, ReorderAlert> bySku = new LinkedHashMap<>();
        alerts.forEach(alert -> bySku.put(alert.sku(), alert));

        String title = "Estoque abaixo do ponto de reposição";
        StringBuilder body = new StringBuilder(bySku.size() == 1
                ? "O SKU a seguir está abaixo do ponto de reposição:"
                : bySku.size() + " SKUs estão abaixo do ponto de reposição:");
        bySku.values().forEach(alert -> body.append("\n- ").append(alert.sku())
                .append(": saldo ").append(alert.quantity())
                .append(", mínimo ").append(alert.minQuantity()));

        userRepository.findUsernamesByPermission(STOCK_MANAGE_PERMISSION)
                .forEach(username -> notificationUseCase.notify(username, NotificationType.SYSTEM,
                        title, body.toString()));
    }
}
