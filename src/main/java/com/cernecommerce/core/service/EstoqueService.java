package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException;
import com.cernecommerce.core.domain.exception.estoque.KitCostNotEditableException;
import com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException;
import com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException;
import com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException;
import com.cernecommerce.core.domain.exception.estoque.LotExpiryDateMismatchException;
import com.cernecommerce.core.domain.exception.estoque.MissingLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockLotNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotActiveException;
import com.cernecommerce.core.domain.exception.estoque.StockReservationNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.UnexpectedLotInfoException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderAlert;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.ReservationStatus;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.estoque.KitComponentRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockLotRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.StockReservationRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class EstoqueService implements EstoqueUseCase {

    private static final String STOCK_MANAGE_PERMISSION = "ESTOQUE_STOCK_MANAGE";
    private static final String REORDER_ALERT_BATCH = "estoque.reorder-alerts";
    private static final String DEFAULT_WAREHOUSE_CONFIG_KEY = "estoque.warehouse.default-code";

    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReorderPointRepository reorderPointRepository;
    private final StockIntegrityRepository stockIntegrityRepository;
    private final StockCountRepository stockCountRepository;
    private final StockReservationRepository stockReservationRepository;
    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;
    private final AfterCommitExecutor afterCommitExecutor;
    private final Duration defaultReservationTtl;
    private final KitComponentRepository kitComponentRepository;
    private final StockLotRepository stockLotRepository;
    private final SystemConfigPort systemConfigPort;

    public EstoqueService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
            StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository,
            ReorderPointRepository reorderPointRepository, StockIntegrityRepository stockIntegrityRepository,
            StockCountRepository stockCountRepository, StockReservationRepository stockReservationRepository,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            AfterCommitExecutor afterCommitExecutor, Duration defaultReservationTtl,
            KitComponentRepository kitComponentRepository, StockLotRepository stockLotRepository,
            SystemConfigPort systemConfigPort) {
        this.stockReservationRepository = stockReservationRepository;
        this.defaultReservationTtl = defaultReservationTtl;
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
        this.kitComponentRepository = kitComponentRepository;
        this.stockLotRepository = stockLotRepository;
        this.systemConfigPort = systemConfigPort;
    }

    @Override
    @Transactional
    public Product createProduct(String sku, String name, String category, List<ProductVariant> variants,
            Pricing pricing) {
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
        Product product = Product.create(sku, name, category, safeVariants,
                pricing == null ? Pricing.empty() : pricing);
        return productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> listProducts(int page, int size) {
        return productRepository.findAll(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> listActivePricedProducts(int page, int size) {
        return productRepository.findAllActiveAndPriced(page, size);
    }

    @Override
    @Transactional
    public Product updateProduct(String sku, String name, String category, Pricing pricing) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        Product updated = current.withDetails(name, category);
        if (pricing != null) {
            // Custo de kit é sempre derivado da soma dos componentes (EST-F015) — um costPrice
            // digitado aqui viraria dado morto, sobrescrito na próxima leitura de
            // findPricingBySku. Rejeitado, não aceito e ignorado.
            if (current.isKit() && pricing.costPrice() != null) {
                throw new KitCostNotEditableException(sku);
            }
            // withPatch e não substituição: um PATCH que manda só o custo não pode apagar o
            // markup e o preço já cadastrados. Cada campo de Pricing carrega a mesma semântica
            // de "nulo mantém" que name e category têm em withDetails.
            updated = updated.withPricing(current.pricing().withPatch(
                    pricing.costPrice(), pricing.markupPercent(), pricing.salePrice()));
        }
        return productRepository.save(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Pricing findPricingBySku(String sku) {
        // findByAnySku e não findBySku: a variação herda o preço do pai, então o SKU lido no
        // balcão resolve para a Pricing do pai sem o chamador precisar saber se é pai ou filho.
        Product product = productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        return product.isKit() ? derivedKitPricing(product) : product.pricing();
    }

    /**
     * Custo do kit é a soma de {@code costPrice * quantity} dos componentes — nunca digitado
     * (EST-F015, §2.10). O {@code salePrice} continua sendo o do kit; {@code markupPercent} é
     * sempre nulo no derivado, porque não é input de ninguém para um kit.
     *
     * <p>Um componente sem custo próprio torna o custo do kit inteiro {@code null} — seguindo a
     * convenção já estabelecida em {@link Pricing} de que ausência é "desconhecido", nunca zero.
     * Isso propaga honestamente: {@code marginPercent()}/{@code marginAmount()}/
     * {@code isBelowCost()} do kit também viram {@code null}, sem precisar de matemática nova.</p>
     */
    private Pricing derivedKitPricing(Product kit) {
        List<KitComponent> recipe = kitComponentRepository.findByKitSku(kit.sku());
        BigDecimal totalCost = BigDecimal.ZERO;
        for (KitComponent component : recipe) {
            // Componente é garantidamente SIMPLES (validado em defineKitRecipe) — lê o Pricing
            // próprio dele direto, sem reentrar em findPricingBySku.
            Product componentProduct = productRepository.findByAnySku(component.componentSku())
                    .orElseThrow(() -> new ProductNotFoundException(component.componentSku()));
            BigDecimal componentCost = componentProduct.pricing().costPrice();
            if (componentCost == null) {
                totalCost = null;
                break;
            }
            totalCost = totalCost.add(componentCost.multiply(component.quantity()));
        }
        return Pricing.of(totalCost, null, kit.pricing().salePrice());
    }

    @Override
    @Transactional(readOnly = true)
    public Product findProductBySku(String sku) {
        // Mesma razão de findPricingBySku: a variação não tem categoria própria, herda a do pai.
        return productRepository.findByAnySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
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
    public Product setProductLotTracked(String sku, boolean lotTracked) {
        Product current = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
        // Kit x lote-rastreado é mutuamente exclusivo — o compact constructor de Product já barra
        // isso, então não há checagem duplicada aqui.
        return productRepository.save(current.withLotTracked(lotTracked));
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
        Optional<Product> product = productRepository.findByAnySku(sku);
        if (product.isPresent() && product.get().isKit()) {
            return derivedKitBalance(product.get(), warehouse.id());
        }
        return stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockLot> listStockLots(String sku, String warehouseCode) {
        Warehouse warehouse = requireWarehouse(warehouseCode);
        return stockLotRepository.findBySkuAndWarehouseId(sku, warehouse.id());
    }

    /**
     * {@code min(floor(saldo_componente_disponível / quantidade_na_receita))} sobre os
     * componentes (EST-F015, §2.10). Usa o <b>disponível</b>, não o físico bruto — consistente
     * com a regra já estabelecida em EST-F021 de que toda saída nova valida contra o disponível:
     * um componente reservado para outro pedido não está de fato livre para montar este kit
     * agora. Recipe vazia (produto ainda não teve receita definida) devolve zero.
     */
    private StockBalance derivedKitBalance(Product kit, Long warehouseId) {
        List<KitComponent> recipe = kitComponentRepository.findByKitSku(kit.sku());
        if (recipe.isEmpty()) {
            return StockBalance.derived(kit.sku(), warehouseId, BigDecimal.ZERO);
        }
        BigDecimal minKits = null;
        for (KitComponent component : recipe) {
            StockBalance componentBalance = stockBalanceRepository
                    .findBySkuAndWarehouseId(component.componentSku(), warehouseId)
                    .orElseGet(() -> StockBalance.zero(component.componentSku(), warehouseId));
            BigDecimal possibleKits = componentBalance.availableQuantity()
                    .divide(component.quantity(), 0, RoundingMode.FLOOR);
            if (minKits == null || possibleKits.compareTo(minKits) < 0) {
                minKits = possibleKits;
            }
        }
        return StockBalance.derived(kit.sku(), warehouseId, minKits);
    }

    @Override
    @Transactional
    public StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username) {
        return adjustStock(sku, warehouseCode, type, quantity, reason, username, null, null);
    }

    @Override
    @Transactional
    public StockBalance adjustStock(String sku, String warehouseCode, MovementType type, BigDecimal quantity,
            String reason, String username, String lotCode, LocalDate expiryDate) {
        requireKnownSku(sku);
        Warehouse warehouse = warehouseRepository.findByCode(warehouseCode)
                .orElseThrow(() -> new WarehouseNotFoundException(warehouseCode));
        Optional<Product> product = productRepository.findByAnySku(sku);
        if (product.isPresent() && product.get().isKit()) {
            // Kit não tem lote próprio (EST-F015: sem saldo físico próprio, e Product já barra
            // lotTracked num kit) — recusar explicitamente em vez de explodeKitMovement descartar
            // lotCode/expiryDate em silêncio na recursão para os componentes.
            if ((lotCode != null && !lotCode.isBlank()) || expiryDate != null) {
                throw new UnexpectedLotInfoException(sku, "kit não tem lote próprio");
            }
            return explodeKitMovement(product.get(), warehouse, type, quantity, reason, username);
        }
        requireActiveForInbound(sku, warehouse, type);
        boolean lotTracked = product.map(Product::lotTracked).orElse(false);
        validateLotInfo(sku, type, lotTracked, lotCode, expiryDate);

        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        StockBalance updated = current.apply(type, quantity);

        String movementLotCode = null;
        if (lotTracked && type == MovementType.ENTRADA) {
            receiveIntoLot(sku, warehouse.id(), lotCode, expiryDate, quantity);
            movementLotCode = lotCode;
        }

        stockMovementRepository.save(
                StockMovement.create(sku, warehouse.id(), type, quantity, reason, username, movementLotCode));
        StockBalance saved = stockBalanceRepository.save(updated);

        if (lotTracked && type == MovementType.SAIDA) {
            consumeLotsFefo(sku, warehouse.id(), quantity);
        }

        notifyIfBelowReorderPoint(saved);
        return saved;
    }

    /**
     * Lote/validade só fazem sentido numa {@code ENTRADA} de SKU lote-rastreado (EST-F008):
     * {@code SAIDA} consome por FEFO automaticamente — o chamador não escolhe o lote — e
     * {@code AJUSTE} direto num SKU lote-rastreado é barrado à parte (ver
     * {@code requireActiveForInbound}-like check em bloco futuro). Ausência/presença fora do
     * esperado é rejeitada explicitamente, não ignorada silenciosamente — mesma régua que
     * {@code Pricing} usa para "nulo é desconhecido, não é ausência sem consequência".
     */
    private void validateLotInfo(String sku, MovementType type, boolean lotTracked, String lotCode,
            LocalDate expiryDate) {
        boolean lotInfoProvided = (lotCode != null && !lotCode.isBlank()) || expiryDate != null;
        if (lotInfoProvided && !lotTracked) {
            throw new UnexpectedLotInfoException(sku, "produto não é lote-rastreado");
        }
        if (lotInfoProvided && type != MovementType.ENTRADA) {
            throw new UnexpectedLotInfoException(sku, "lote só se aplica a ENTRADA — SAIDA consome por FEFO");
        }
        if (lotTracked && type == MovementType.ENTRADA
                && (lotCode == null || lotCode.isBlank() || expiryDate == null)) {
            throw new MissingLotInfoException(sku);
        }
    }

    /**
     * Upsert no lote (EST-F008): soma a quantidade recebida, criando a linha se for a primeira
     * entrada daquele {@code lotCode}. A validade grava na criação e não muda depois — um
     * reabastecimento do mesmo lote informando outra validade é erro de digitação.
     */
    private void receiveIntoLot(String sku, Long warehouseId, String lotCode, LocalDate expiryDate,
            BigDecimal quantity) {
        StockLot lot = stockLotRepository.findBySkuAndWarehouseIdAndLotCode(sku, warehouseId, lotCode)
                .orElseGet(() -> StockLot.create(sku, warehouseId, lotCode, expiryDate));
        if (!lot.expiryDate().equals(expiryDate)) {
            throw new LotExpiryDateMismatchException(sku, lotCode, lot.expiryDate(), expiryDate);
        }
        stockLotRepository.save(lot.receive(quantity));
    }

    /**
     * Consome os lotes do par SKU/depósito por FEFO — do que vence primeiro em diante — até cobrir
     * {@code quantity}. Quem valida se existe saldo suficiente é {@code stock_balance}, já aplicado
     * antes desta chamada; se os lotes não cobrirem o total (descompasso entre o agregado e a soma
     * dos lotes — só possível por drift externo), consome o que existe e não lança erro: a venda já
     * foi validada contra o agregado, e o descompasso fica visível em
     * {@code GET /estoque/integrity/lot-mismatch} em vez de derrubar a operação.
     */
    private void consumeLotsFefo(String sku, Long warehouseId, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        for (StockLot lot : stockLotRepository.findBySkuAndWarehouseId(sku, warehouseId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            if (lot.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal drawn = lot.quantity().min(remaining);
            stockLotRepository.save(lot.consume(drawn));
            remaining = remaining.subtract(drawn);
        }
    }

    /**
     * Explode a movimentação de um kit em uma por componente (EST-F015, §2.10) — venda e
     * estorno passam por aqui transparentemente, sem que {@code PdvService}/{@code OrderService}
     * precisem saber que o SKU vendido é um kit. Autoinvocação de {@link #adjustStock} dentro da
     * mesma classe não passa pelo proxy Spring, então continua na MESMA transação ambiente: a
     * venda/estorno do kit é atômica com o resto do pedido.
     */
    private StockBalance explodeKitMovement(Product kit, Warehouse warehouse, MovementType type,
            BigDecimal kitQuantity, String reason, String username) {
        if (type == MovementType.AJUSTE) {
            // Kit não tem saldo próprio nem contagem física própria — nada para ajustar
            // diretamente. Balanço de inventário deve contar os componentes.
            throw new KitDirectAdjustmentException(kit.sku());
        }
        List<KitComponent> recipe = kitComponentRepository.findByKitSku(kit.sku());
        if (recipe.isEmpty()) {
            throw new EmptyKitRecipeException(kit.sku());
        }
        String kitReason = reason + " (kit " + kit.sku() + ")";
        for (KitComponent component : recipe) {
            BigDecimal componentQuantity = component.quantity().multiply(kitQuantity);
            adjustStock(component.componentSku(), warehouse.code(), type, componentQuantity, kitReason, username);
        }
        // Kit não tem linha própria em stock_balance: devolve o saldo derivado recalculado,
        // nunca o de um componente qualquer (seria a unidade errada).
        return derivedKitBalance(kit, warehouse.id());
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

    @Override
    @Transactional(readOnly = true)
    public PageResult<ReservationIntegrityMismatch> listReservationMismatches(int page, int size) {
        return stockIntegrityRepository.findReservationMismatches(page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<LotIntegrityMismatch> listLotMismatches(int page, int size) {
        return stockIntegrityRepository.findLotMismatches(page, size);
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
        return recordCountedItem(stockCountId, sku, countedQuantity, null);
    }

    @Override
    @Transactional
    public StockCount recordCountedItem(Long stockCountId, String sku, BigDecimal countedQuantity, String lotCode) {
        StockCount count = requireOpenStockCount(stockCountId);
        // Mesma pré-condição de adjustStock (EST-C002): não adianta contar um SKU que o
        // fechamento não conseguiria ajustar.
        requireKnownSku(sku);
        Optional<Product> product = productRepository.findByAnySku(sku);
        // Kit não tem contagem física própria (EST-F015) — rejeitar aqui, na hora do registro,
        // em vez de deixar o erro só aparecer confusamente quando closeStockCount tentar um
        // AJUSTE direto no kit e abortar o fechamento inteiro.
        product.filter(Product::isKit).ifPresent(kit -> { throw new KitDirectAdjustmentException(sku); });

        // EST-F008: SKU lote-rastreado é contado por lote, não agregado — mesma régua condicional
        // de validateLotInfo, mas aqui não há tipo de movimento: a contagem em si já é a operação.
        boolean lotTracked = product.map(Product::lotTracked).orElse(false);
        boolean lotInfoProvided = lotCode != null && !lotCode.isBlank();
        if (lotTracked && !lotInfoProvided) {
            throw new MissingLotInfoException(sku);
        }
        if (!lotTracked && lotInfoProvided) {
            throw new UnexpectedLotInfoException(sku, "produto não é lote-rastreado");
        }
        if (lotInfoProvided) {
            // Contagem só reconcilia lote que já existe — lote novo entra por recebimento
            // (adjustStock ENTRADA), não pelo balanço.
            stockLotRepository.findBySkuAndWarehouseIdAndLotCode(sku, count.warehouseId(), lotCode)
                    .orElseThrow(() -> new StockLotNotFoundException(sku, lotCode));
        }
        return stockCountRepository.save(count.withCountedItem(sku, countedQuantity, lotCode));
    }

    @Override
    @Transactional
    public StockCount closeStockCount(Long stockCountId, String username) {
        StockCount count = requireOpenStockCount(stockCountId);
        Warehouse warehouse = getWarehouse(count.warehouseId());

        Map<String, List<StockCountItem>> bySku = new LinkedHashMap<>();
        count.items().forEach(item -> bySku.computeIfAbsent(item.sku(), k -> new ArrayList<>()).add(item));

        List<StockCountItem> reconciled = new ArrayList<>();
        for (Map.Entry<String, List<StockCountItem>> entry : bySku.entrySet()) {
            String sku = entry.getKey();
            List<StockCountItem> items = entry.getValue();
            boolean lotTracked = items.stream().anyMatch(i -> i.lotCode() != null);
            if (lotTracked) {
                reconciled.addAll(closeLotTrackedSku(stockCountId, warehouse, sku, items, username));
            } else {
                reconciled.add(closeAggregateSku(stockCountId, warehouse, items.get(0), username));
            }
        }
        return stockCountRepository.save(count.withReconciledItems(reconciled).closed());
    }

    /**
     * Fechamento de um SKU não lote-rastreado: confronta contra o agregado de
     * {@code stock_balance}, como sempre foi. Contagem que bateu não vira movimentação — um AJUSTE
     * de saldo para ele mesmo só faria ruído no ledger.
     */
    private StockCountItem closeAggregateSku(Long stockCountId, Warehouse warehouse, StockCountItem item,
            String username) {
        BigDecimal systemQuantity = stockBalanceRepository
                .findBySkuAndWarehouseId(item.sku(), warehouse.id())
                .map(StockBalance::quantity)
                .orElse(BigDecimal.ZERO);
        StockCountItem confronted = item.reconciledWith(systemQuantity);
        if (confronted.diverges()) {
            adjustStock(item.sku(), warehouse.code(), MovementType.AJUSTE, item.countedQuantity(),
                    "Balanço de inventário #" + stockCountId, username);
        }
        return confronted;
    }

    /**
     * Fechamento de um SKU lote-rastreado (EST-F008): cada lote contado é confrontado e
     * reconciliado contra o próprio {@code StockLot}, não contra o agregado — é isto que mantém
     * {@code SUM(stock_lot.quantity) == stock_balance.quantity} depois de um balanço, em vez de
     * deixar a soma dos lotes derivar silenciosamente do agregado (o gap que {@code
     * StockLot.reconciledTo} existia para fechar e ninguém chamava). Só depois de reconciliar todo
     * lote do grupo é que o agregado recebe UM AJUSTE para a soma — mesmo formato de ledger que o
     * SKU não lote-rastreado, um evento por SKU por fechamento, não um por lote.
     */
    private List<StockCountItem> closeLotTrackedSku(Long stockCountId, Warehouse warehouse, String sku,
            List<StockCountItem> items, String username) {
        List<StockCountItem> reconciled = new ArrayList<>();
        BigDecimal newTotal = BigDecimal.ZERO;
        for (StockCountItem item : items) {
            StockLot lot = stockLotRepository.findBySkuAndWarehouseIdAndLotCode(sku, warehouse.id(), item.lotCode())
                    .orElseThrow(() -> new StockLotNotFoundException(sku, item.lotCode()));
            StockCountItem confronted = item.reconciledWith(lot.quantity());
            reconciled.add(confronted);
            if (confronted.diverges()) {
                stockLotRepository.save(lot.reconciledTo(item.countedQuantity()));
            }
            newTotal = newTotal.add(item.countedQuantity());
        }

        BigDecimal systemQuantity = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .map(StockBalance::quantity)
                .orElse(BigDecimal.ZERO);
        if (newTotal.compareTo(systemQuantity) != 0) {
            adjustStock(sku, warehouse.code(), MovementType.AJUSTE, newTotal,
                    "Balanço de inventário #" + stockCountId, username);
        }
        return reconciled;
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

    @Override
    @Transactional(readOnly = true)
    public Warehouse getWarehouseByCode(String code) {
        return requireWarehouse(code);
    }

    @Override
    @Transactional(readOnly = true)
    public Warehouse getDefaultWarehouse() {
        String code = systemConfigPort.findByKey(DEFAULT_WAREHOUSE_CONFIG_KEY)
                .map(SystemConfig::value)
                .filter(value -> !value.isBlank())
                .orElseThrow(DefaultWarehouseNotConfiguredException::new);
        return requireWarehouse(code);
    }

    // ---------------------------------------------------------------------------------------
    // Reserva de estoque (EST-F021)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public StockReservation reserveStock(String sku, String warehouseCode, BigDecimal quantity,
            String ownerReference, Duration ttl, String username) {
        // Mesmas pré-condições de adjustStock (EST-C002): não adianta reservar um SKU que a baixa
        // depois não conseguiria movimentar.
        requireKnownSku(sku);
        Warehouse warehouse = requireWarehouse(warehouseCode);

        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(sku, warehouse.id())
                .orElseGet(() -> StockBalance.zero(sku, warehouse.id()));
        // A escrita do contador é o que serializa: duas reservas concorrentes do mesmo SKU disputam
        // esta linha e uma delas sai em 409, em vez de as duas passarem e o disponível ficar negativo.
        StockBalance reserved = stockBalanceRepository.save(current.reserve(quantity));

        Duration effectiveTtl = ttl == null ? defaultReservationTtl : ttl;
        StockReservation reservation = stockReservationRepository.save(StockReservation.create(
                sku, warehouse.id(), quantity, ownerReference,
                Instant.now().plus(effectiveTtl), username));

        // O alerta de reposição olha o disponível, não o físico: quando um pedido online segura a
        // última unidade, é nesse instante que se precisa repor — não quando ela for despachada.
        notifyIfBelowReorderPoint(reserved);
        return reservation;
    }

    @Override
    @Transactional
    public StockReservation consumeReservation(Long reservationId, String username) {
        StockReservation reservation = requireActiveReservation(reservationId);
        Warehouse warehouse = getWarehouse(reservation.warehouseId());

        StockBalance current = stockBalanceRepository.findBySkuAndWarehouseId(
                        reservation.sku(), reservation.warehouseId())
                .orElseGet(() -> StockBalance.zero(reservation.sku(), reservation.warehouseId()));

        // Não passa por apply(SAIDA): aquele caminho valida contra o disponível, e o disponível
        // aqui já está descontado desde a reserva — validá-lo de novo recusaria a própria reserva
        // que estamos consumindo. O físico e o reservado caem juntos.
        StockBalance updated = stockBalanceRepository.save(current.consumeReservation(reservation.quantity()));

        // A mercadoria saiu de verdade: o ledger tem que registrar, com o mesmo formato das outras
        // saídas, senão o histórico do SKU fica com um buraco do tamanho das vendas online.
        stockMovementRepository.save(StockMovement.create(reservation.sku(), reservation.warehouseId(),
                MovementType.SAIDA, reservation.quantity(),
                "Consumo da reserva #" + reservationId + " (" + reservation.ownerReference() + ")", username));

        // EST-F008: a mercadoria reservada também sai de um lote de verdade quando o SKU é
        // lote-rastreado — este caminho não passa por adjustStock, então o FEFO precisa ser
        // disparado aqui também, senão stock_lot nunca desconta a venda do marketplace.
        productRepository.findByAnySku(reservation.sku())
                .filter(Product::lotTracked)
                .ifPresent(p -> consumeLotsFefo(reservation.sku(), reservation.warehouseId(), reservation.quantity()));

        notifyIfBelowReorderPoint(updated);
        return stockReservationRepository.save(reservation.consumed());
    }

    @Override
    @Transactional
    public StockReservation releaseReservation(Long reservationId, String username) {
        return stockReservationRepository.save(releaseInternal(requireActiveReservation(reservationId)));
    }

    @Override
    @Transactional
    public int releaseReservationsByOwner(String ownerReference, String username) {
        List<StockReservation> active = stockReservationRepository.findActiveByOwnerReference(ownerReference);
        active.forEach(reservation -> stockReservationRepository.save(releaseInternal(reservation)));
        return active.size();
    }

    @Override
    @Transactional
    public int consumeReservationsByOwner(String ownerReference, String username) {
        List<StockReservation> active = stockReservationRepository.findActiveByOwnerReference(ownerReference);
        active.forEach(reservation -> consumeReservation(reservation.id(), username));
        return active.size();
    }

    @Override
    @Transactional(readOnly = true)
    public StockReservation getStockReservation(Long reservationId) {
        return stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new StockReservationNotFoundException(reservationId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StockReservation> listReservations(String sku, String warehouseCode,
            ReservationStatus status, int page, int size) {
        Long warehouseId = warehouseCode == null ? null : requireWarehouse(warehouseCode).id();
        return stockReservationRepository.findByFilters(sku, warehouseId, status, page, size);
    }

    @Override
    @Transactional
    public int expireReservations(int batchSize) {
        List<StockReservation> expired = stockReservationRepository.findExpired(Instant.now(), batchSize);
        for (StockReservation reservation : expired) {
            devolveReservedQuantity(reservation);
            stockReservationRepository.save(reservation.expired());
        }
        return expired.size();
    }

    @Override
    @Transactional
    public int alertExpiringLots(int cutoffDays, int batchSize) {
        List<StockLot> expiring = stockLotRepository.findExpiringSoon(LocalDate.now().plusDays(cutoffDays), batchSize);
        if (expiring.isEmpty()) {
            return 0;
        }
        dispatchLotExpiryAlerts(expiring);
        expiring.forEach(lot -> stockLotRepository.save(lot.alerted()));
        return expiring.size();
    }

    /**
     * Uma notificação por passada do varredor, não uma por lote — quem recebe não precisa de N
     * notificações separadas para N lotes vencendo na mesma manhã. Mesmo formato de
     * {@link #dispatchReorderAlerts}, mas sem passar pelo {@code afterCommitExecutor}: aquele
     * existe para agrupar chamadas que acontecem no meio de uma transação de negócio maior (venda,
     * reserva) e só disparar depois do commit; aqui o próprio método já É a transação de topo do
     * varredor, não há nada maior para esperar committar.
     */
    private void dispatchLotExpiryAlerts(List<StockLot> lots) {
        String title = "Lote de estoque vencendo";
        StringBuilder body = new StringBuilder(lots.size() == 1
                ? "O lote a seguir está vencendo:"
                : lots.size() + " lotes estão vencendo:");
        lots.forEach(lot -> body.append("\n- ").append(lot.sku())
                .append(" (lote ").append(lot.lotCode()).append("): vence em ").append(lot.expiryDate())
                .append(", saldo ").append(lot.quantity()));

        userRepository.findUsernamesByPermission(STOCK_MANAGE_PERMISSION)
                .forEach(username -> notificationUseCase.notify(username, NotificationType.SYSTEM,
                        title, body.toString()));
    }

    // ---------------------------------------------------------------------------------------
    // Kits (EST-F015) — virtuais, de um nível só (§2.10 do plano)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional
    public Product defineKitRecipe(String kitSku, List<KitComponentCommand> components) {
        // findBySku, não findByAnySku: kit é sempre SKU pai, nunca uma variação.
        Product kit = productRepository.findBySku(kitSku)
                .orElseThrow(() -> new ProductNotFoundException(kitSku));
        if (components == null || components.isEmpty()) {
            throw new EmptyKitRecipeException(kitSku);
        }
        // Kit e variações são mutuamente exclusivos: não há endpoint para adicionar variação
        // depois da criação (ver Product.withDetails), então checar aqui, na promoção, fecha o
        // espaço todo com uma linha só.
        if (!kit.variants().isEmpty()) {
            throw new KitHasVariantsException(kitSku);
        }
        // Sustenta a invariante de um nível só contra a porta dos fundos: sem isto, promover a
        // KIT um SKU que já é componente de outro kit criaria kit-dentro-de-kit sem nunca passar
        // pela checagem "componente precisa ser SIMPLES", que só roda no sentido contrário.
        if (kitComponentRepository.isUsedAsComponent(kitSku)) {
            throw new KitComponentAlreadyInUseException(kitSku);
        }

        Set<String> seen = new LinkedHashSet<>();
        List<KitComponent> recipe = new ArrayList<>();
        for (KitComponentCommand command : components) {
            String componentSku = command.componentSku();
            if (componentSku.equals(kitSku)) {
                throw new KitSelfReferenceException(kitSku);
            }
            if (!seen.add(componentSku)) {
                throw new DuplicateKitComponentException(kitSku, componentSku);
            }
            Product component = productRepository.findByAnySku(componentSku)
                    .orElseThrow(() -> new ProductNotFoundException(componentSku));
            if (component.isKit()) {
                throw new KitComponentNotSimpleException(kitSku, componentSku, component.type());
            }
            recipe.add(KitComponent.create(kitSku, componentSku, command.quantity()));
        }

        kitComponentRepository.replaceRecipe(kitSku, recipe);
        return productRepository.save(kit.withType(ProductType.KIT));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitComponent> getKitRecipe(String kitSku) {
        // Confirma que o SKU existe antes de devolver lista vazia — SKU desconhecido é 404, SKU
        // que existe mas nunca foi kit é lista vazia. Duas coisas diferentes.
        productRepository.findBySku(kitSku).orElseThrow(() -> new ProductNotFoundException(kitSku));
        return kitComponentRepository.findByKitSku(kitSku);
    }

    private StockReservation releaseInternal(StockReservation reservation) {
        devolveReservedQuantity(reservation);
        return reservation.released();
    }

    /**
     * Devolve a quantidade ao disponível sem tocar no físico — nada entrou nem saiu da prateleira,
     * então <b>não</b> gera {@link StockMovement}. Registrar uma movimentação aqui inflaria o ledger
     * com entradas de mercadoria que nunca se moveu.
     */
    private void devolveReservedQuantity(StockReservation reservation) {
        stockBalanceRepository.findBySkuAndWarehouseId(reservation.sku(), reservation.warehouseId())
                .ifPresent(balance -> stockBalanceRepository.save(
                        balance.releaseReservation(reservation.quantity())));
    }

    private StockReservation requireActiveReservation(Long reservationId) {
        StockReservation reservation = stockReservationRepository.findById(reservationId)
                .orElseThrow(() -> new StockReservationNotFoundException(reservationId));
        if (!reservation.isActive()) {
            throw new StockReservationNotActiveException(reservationId, reservation.status());
        }
        return reservation;
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
        // Compara contra o DISPONÍVEL, não o físico (EST-F021): unidade reservada para um pedido
        // online não está à venda, e esperar o despacho para avisar atrasaria a reposição em todo o
        // tempo de separação. Enquanto não houver reserva, disponível == físico e o comportamento
        // anterior fica idêntico.
        BigDecimal available = balance.availableQuantity();
        reorderPointRepository.findBySkuAndWarehouseId(balance.sku(), balance.warehouseId())
                .filter(reorderPoint -> reorderPoint.isBelow(available))
                .ifPresent(reorderPoint -> afterCommitExecutor.accumulate(REORDER_ALERT_BATCH,
                        new ReorderAlert(balance.sku(), available, reorderPoint.minQuantity()),
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
