package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderAlert;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
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
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final StockCountRepository stockCountRepository;
    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final AfterCommitExecutor afterCommitExecutor;

    public EstoqueService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository,
            ReorderPointRepository reorderPointRepository, StockIntegrityRepository stockIntegrityRepository,
            StockCountRepository stockCountRepository, NotificationUseCase notificationUseCase,
            UserRepository userRepository, AfterCommitExecutor afterCommitExecutor) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.stockBalanceRepository = stockBalanceRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.reorderPointRepository = reorderPointRepository;
        this.stockIntegrityRepository = stockIntegrityRepository;
        this.stockCountRepository = stockCountRepository;
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
    public Product updateProduct(String sku, String name, String category) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return productRepository.save(current.withDetails(name, category));
    }

    @Override
    @Transactional
    public Product setProductActive(String sku, boolean active) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return productRepository.save(current.withActive(active));
    }

    @Override
    @Transactional
    public Warehouse updateWarehouse(String code, String name, WarehouseType type) {
        Warehouse current = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
        return warehouseRepository.save(current.withDetails(name, type));
    }

    @Override
    @Transactional
    public Warehouse setWarehouseActive(String code, boolean active) {
        Warehouse current = warehouseRepository.findByCode(code)
                .orElseThrow(() -> new WarehouseNotFoundException(code));
        return warehouseRepository.save(current.withActive(active));
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
    public PageResult<Warehouse> listWarehouses(int page, int size) {
        return warehouseRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public StockBalance getStockBalance(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
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
        requireActiveForInbound(sku, warehouse, type);
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

    // ---------------------------------------------------------------------------------------
    // Balanço de inventário (EST-F006)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public StockCount openStockCount(String warehouseCode, String username) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        stockCountRepository.findOpenByWarehouseId(warehouse.id()).ifPresent(open -> {
            throw new StockCountAlreadyOpenException(warehouseCode, open.id());
        });
        return stockCountRepository.save(StockCount.open(warehouse.id(), username));
    }

    @Override
    @Transactional
    public StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity) {
        StockCount count = requireOpenStockCount(stockCountId);
        // Mesma pré-condição de adjustStock (EST-C002): não adianta contar um SKU que o
        // fechamento não conseguiria ajustar.
        requireKnownSku(sku);
        return stockCountRepository.save(count.withCountedItem(sku, countedQuantity));
    }

    @Override
    @Transactional
    public StockCount closeStockCount(Long stockCountId, String username) {
        StockCount count = requireOpenStockCount(stockCountId);
        Warehouse warehouse = getWarehouse(count.warehouseId());

        List<StockCountItem> reconciled = new ArrayList<>();
        for (StockCountItem item : count.items()) {
            BigDecimal systemQuantity = stockBalanceRepository
                    .findBySkuAndWarehouseId(item.sku(), warehouse.id())
                    .map(StockBalance::quantity)
                    .orElse(BigDecimal.ZERO);
            StockCountItem confronted = item.reconciledWith(systemQuantity);
            reconciled.add(confronted);
            // Contagem que bateu não vira movimentação: o ledger registra o que mudou, e um
            // AJUSTE de saldo para ele mesmo só faria ruído.
            if (confronted.diverges()) {
                adjustStock(item.sku(), warehouse.code(), MovementType.AJUSTE, item.countedQuantity(),
                        "Balanço de inventário #" + stockCountId, username);
            }
        }
        return stockCountRepository.save(count.withReconciledItems(reconciled).closed());
    }

    @Override
    @Transactional
    public StockCount cancelStockCount(Long stockCountId) {
        return stockCountRepository.save(requireOpenStockCount(stockCountId).cancelled());
    }

    @Override
    @Transactional(readOnly = true)
    public StockCount getStockCount(Long stockCountId) {
        return stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new StockCountNotFoundException(stockCountId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockCount> listStockCounts(String warehouseCode, int page, int size) {
        return stockCountRepository.findByWarehouseId(requireWarehouse(warehouseCode).id(), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse getWarehouse(Long warehouseId) {
        return warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new WarehouseNotFoundException(String.valueOf(warehouseId)));
    }

    private Warehouse requireWarehouse(String warehouseCode) {
        return warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
    }

    private StockCount requireOpenStockCount(Long stockCountId) {
        StockCount count = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new StockCountNotFoundException(stockCountId));
        if (!count.isOpen()) {
            throw new StockCountNotOpenException(stockCountId, count.status());
        }
        return count;
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
     * Produto ou depósito desativado recusa <b>entrada</b> de estoque (EST-F018), seja ela manual
     * ou vinda de um recebimento de Compras.
     *
     * <p>Só {@code ENTRADA} é barrada. {@code SAIDA} continua livre de propósito — desativar
     * significa "não reponho mais", e bloquear a saída deixaria preso o saldo que ainda existe na
     * prateleira. {@code AJUSTE} também passa: é o caminho de correção de inventário, e um
     * produto desativado com contagem errada precisa poder ser acertado.</p>
     */
    private void requireActiveForInbound(String sku, Warehouse warehouse, MovementType type) {
        if (type != MovementType.ENTRADA) {
            return;
        }
        if (!warehouse.active()) {
            throw new InactiveWarehouseException(warehouse.code());
        }
        if (!productRepository.isSkuActive(sku)) {
            throw new InactiveProductException(sku);
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
