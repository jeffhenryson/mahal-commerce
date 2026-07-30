package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InactiveProductException;
import com.cernecommerce.core.domain.exception.estoque.InactiveWarehouseException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountAlreadyOpenException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.StockCountNotOpenException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitComponentException;
import com.cernecommerce.core.domain.exception.estoque.EmptyKitRecipeException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentAlreadyInUseException;
import com.cernecommerce.core.domain.exception.estoque.KitComponentNotSimpleException;
import com.cernecommerce.core.domain.exception.estoque.KitCostNotEditableException;
import com.cernecommerce.core.domain.exception.estoque.KitDirectAdjustmentException;
import com.cernecommerce.core.domain.exception.estoque.KitHasVariantsException;
import com.cernecommerce.core.domain.exception.estoque.KitSelfReferenceException;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase.KitComponentCommand;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.estoque.KitComponentRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockCountRepository;
import com.cernecommerce.core.ports.out.estoque.StockIntegrityRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.StockReservationRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock ProductRepository productRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockBalanceRepository stockBalanceRepository;
    @Mock StockMovementRepository stockMovementRepository;
    @Mock ReorderPointRepository reorderPointRepository;
    @Mock StockIntegrityRepository stockIntegrityRepository;
    @Mock StockCountRepository stockCountRepository;
    @Mock StockReservationRepository stockReservationRepository;
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;
    @Mock KitComponentRepository kitComponentRepository;

    /** Mesmo default de {@code estoque.reservation.default-ttl} em {@code CoreBeanConfig}. */
    private static final Duration RESERVATION_TTL = Duration.ofMinutes(30);

    EstoqueService estoqueService;

    /**
     * Sem transação real nestes testes, o executor despacha na hora — é o mesmo comportamento da
     * implementação de produção quando não há sincronização de transação ativa. A agregação em
     * lote é exercitada por {@code TransactionAfterCommitExecutorTest} e pelo IT de alerta.
     */
    private final AfterCommitExecutor immediateExecutor = new AfterCommitExecutor() {
        @Override
        public <T> void accumulate(String key, T item, Consumer<List<T>> flush) {
            flush.accept(List.of(item));
        }
    };

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(productRepository, warehouseRepository, stockBalanceRepository,
                stockMovementRepository, reorderPointRepository, stockIntegrityRepository, stockCountRepository,
                stockReservationRepository, notificationUseCase, userRepository, immediateExecutor,
                RESERVATION_TTL, kitComponentRepository);
        lenient().when(reorderPointRepository.findBySkuAndWarehouseId(any(), any())).thenReturn(Optional.empty());
        // Padrão dos testes: o SKU existe no catálogo, que é a pré-condição das movimentações.
        // Os testes de createProduct e os de SKU desconhecido sobrescrevem este stub.
        lenient().when(productRepository.existsBySku(any())).thenReturn(true);
        // E está ativo, pré-condição só da ENTRADA desde EST-F018. Os testes de produto
        // desativado sobrescrevem.
        lenient().when(productRepository.isSkuActive(any())).thenReturn(true);
    }

    private List<ProductVariant> oneVariant() {
        return List.of(ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "menta"))));
    }

    @Test
    void createProduct_savesAndReturns() {
        Product saved = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenReturn(saved);

        Product result = estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant());

        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.variants()).hasSize(1);
        verify(productRepository).save(any());
    }

    @Test
    void createProduct_throwsWhenSkuAlreadyExists() {
        when(productRepository.existsBySku("NARG-001")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant()))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_allowsProductWithoutVariants() {
        Product saved = Product.of(2L, "CARV-001", "Carvão Coco", "carvao", true, List.of());
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenReturn(saved);

        Product result = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of());

        assertThat(result.variants()).isEmpty();
    }

    @Test
    void createProduct_throwsWhenVariantSkuAlreadyExists() {
        // EST-C010: antes, o SKU de variação duplicado escapava até a constraint
        // uk_product_variant_sku e virava 500 em vez de 409.
        when(productRepository.existsBySku("NARG-001")).thenReturn(false);
        when(productRepository.existsBySku("NARG-M-001")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant()))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("NARG-M-001");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_throwsWhenPayloadRepeatsTheSameVariantSku() {
        List<ProductVariant> duplicated = List.of(
                ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "menta"))),
                ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "uva"))));

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", duplicated))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("NARG-M-001");
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_throwsWhenVariantSkuEqualsParentSku() {
        List<ProductVariant> collidesWithParent = List.of(
                ProductVariant.create("NARG-001", List.of(new ProductAttribute("sabor", "menta"))));

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile",
                collidesWithParent))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void listProducts_delegatesToRepository() {
        PageResult<Product> page = new PageResult<>(
                List.of(Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, List.of())), 0, 20, 1L, 1);
        when(productRepository.findAll(0, 20)).thenReturn(page);

        PageResult<Product> result = estoqueService.listProducts(0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void createWarehouse_savesAndReturns() {
        Warehouse saved = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.empty());
        when(warehouseRepository.save(any())).thenReturn(saved);

        Warehouse result = estoqueService.createWarehouse("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA);

        assertThat(result.code()).isEqualTo("LOJA-01");
        verify(warehouseRepository).save(any());
    }

    @Test
    void createWarehouse_throwsWhenCodeAlreadyExists() {
        when(warehouseRepository.findByCode("LOJA-01"))
                .thenReturn(Optional.of(Warehouse.of(1L, "LOJA-01", "Existente", WarehouseType.LOJA_FISICA, true)));

        assertThatThrownBy(() -> estoqueService.createWarehouse("LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA))
                .isInstanceOf(DuplicateWarehouseCodeException.class);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void listWarehouses_delegatesPagingToRepository() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findAll(1, 50)).thenReturn(new PageResult<>(List.of(warehouse), 1, 50, 1L, 1));

        PageResult<Warehouse> result = estoqueService.listWarehouses(1, 50);

        assertThat(result.content()).containsExactly(warehouse);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(warehouseRepository).findAll(1, 50);
    }

    @Test
    void getStockBalance_returnsExistingBalance() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance balance = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("5.000"), 2L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(balance));

        StockBalance result = estoqueService.getStockBalance("NARG-001", "LOJA-01");

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
    }

    @Test
    void getStockBalance_returnsZeroWhenNoBalanceRecordYet() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());

        StockBalance result = estoqueService.getStockBalance("NARG-001", "LOJA-01");

        assertThat(result.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warehouseId()).isEqualTo(1L);
    }

    @Test
    void getStockBalance_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getStockBalance("NARG-001", "INEXISTENTE"))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    void adjustStock_entrada_withoutPriorBalance_startsFromZeroAndPersists() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento inicial", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
        verify(stockMovementRepository).save(argThat(m -> m.type() == MovementType.ENTRADA
                && m.quantity().compareTo(new BigDecimal("5.000")) == 0
                && m.username().equals("gerente")));
        verify(stockBalanceRepository).save(any());
    }

    @Test
    void adjustStock_saida_decreasesExistingBalance() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 2L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("3.000"), "Venda balcão", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("7.000");
    }

    @Test
    void adjustStock_saida_insufficientBalance_throwsAndDoesNotPersistAnything() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("2.000"), 0L);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("5.000"), "Venda balcão", "gerente"))
                .isInstanceOf(InsufficientStockException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "INEXISTENTE", MovementType.ENTRADA,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    // ── Kits (EST-F015) ──────────────────────────────────────────────────────────────────────

    private Product kitProduct(String sku, String salePrice) {
        return Product.of(1L, sku, "Kit " + sku, "combo", true, List.of(),
                Pricing.of(null, null, new BigDecimal(salePrice)), ProductType.KIT);
    }

    private Product simpleProduct(String sku, String costPrice) {
        BigDecimal cost = costPrice == null ? null : new BigDecimal(costPrice);
        return Product.of(2L, sku, "Componente " + sku, "insumo", true, List.of(),
                Pricing.of(cost, null, new BigDecimal("999.00")));
    }

    @Test
    void findPricingBySku_kit_sumsComponentCosts() {
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", BigDecimal.ONE)));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(simpleProduct("ESS-001", "15.00")));

        Pricing derived = estoqueService.findPricingBySku("KIT-001");

        // 20,00 x 2 + 15,00 x 1 = 55,00.
        assertThat(derived.costPrice()).isEqualByComparingTo("55.00");
        assertThat(derived.salePrice()).isEqualByComparingTo("80.00");
        assertThat(derived.markupPercent()).isNull();
    }

    @Test
    void findPricingBySku_kit_costIsNullWhenAComponentHasNoCost() {
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", BigDecimal.ONE)));
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.findByAnySku("ESS-001")).thenReturn(Optional.of(simpleProduct("ESS-001", null)));

        // Ausência é "desconhecido", nunca zero — mesma convenção de Pricing.
        assertThat(estoqueService.findPricingBySku("KIT-001").costPrice()).isNull();
    }

    @Test
    void getStockBalance_kit_derivesMinFloorAcrossComponents() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2")),
                KitComponent.create("KIT-001", "ESS-001", new BigDecimal("3"))));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "CARV-001", 1L, new BigDecimal("10"), 0L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("ESS-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(11L, "ESS-001", 1L, new BigDecimal("7"), 0L)));

        StockBalance result = estoqueService.getStockBalance("KIT-001", "LOJA-01");

        // CARV-001: 10/2 = 5 kits possíveis; ESS-001: floor(7/3) = 2 kits possíveis; vence o menor.
        assertThat(result.quantity()).isEqualByComparingTo("2");
        assertThat(result.id()).isNull();
    }

    @Test
    void getStockBalance_kit_returnsZeroWhenRecipeEmpty() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of());

        assertThat(estoqueService.getStockBalance("KIT-001", "LOJA-01").quantity())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void adjustStock_kit_explodesIntoOneMovementPerComponent() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of(
                KitComponent.create("KIT-001", "CARV-001", new BigDecimal("2"))));
        when(stockBalanceRepository.findBySkuAndWarehouseId("CARV-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(10L, "CARV-001", 1L, new BigDecimal("10"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("3"),
                "Venda balcão sessão #1", "gerente");

        // 2 (receita) x 3 (kits vendidos) = 6 unidades do componente.
        verify(stockMovementRepository).save(argThat(m -> m.sku().equals("CARV-001")
                && m.type() == MovementType.SAIDA
                && m.quantity().compareTo(new BigDecimal("6")) == 0
                && m.reason().equals("Venda balcão sessão #1 (kit KIT-001)")));
        // Kit nunca ganha linha própria em stock_balance.
        verify(stockMovementRepository, never()).save(argThat(m -> m.sku().equals("KIT-001")));
    }

    @Test
    void adjustStock_kit_rejectsDirectAdjustment() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.AJUSTE,
                BigDecimal.TEN, "contagem", "gerente"))
                .isInstanceOf(KitDirectAdjustmentException.class);

        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void adjustStock_kit_rejectsEmptyRecipe() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(
                Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true)));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(List.of());

        assertThatThrownBy(() -> estoqueService.adjustStock("KIT-001", "LOJA-01", MovementType.SAIDA,
                BigDecimal.ONE, "motivo", "gerente"))
                .isInstanceOf(EmptyKitRecipeException.class);
    }

    @Test
    void defineKitRecipe_promotesProductAndReplacesRecipe() {
        Product product = Product.of(1L, "KIT-001", "Kit Narguile", "combo", true, List.of());
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(product));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product result = estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", new BigDecimal("2"))));

        assertThat(result.type()).isEqualTo(ProductType.KIT);
        verify(kitComponentRepository).replaceRecipe(eq("KIT-001"), argThat(recipe -> recipe.size() == 1
                && recipe.get(0).componentSku().equals("CARV-001")));
    }

    @Test
    void defineKitRecipe_throwsWhenKitSkuNotFound() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentsIsEmpty() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001", List.of()))
                .isInstanceOf(EmptyKitRecipeException.class);
        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001", null))
                .isInstanceOf(EmptyKitRecipeException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenProductHasVariants() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, oneVariant())));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(KitHasVariantsException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenSkuAlreadyComponentOfAnotherKit() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(true);

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("CARV-001", BigDecimal.ONE))))
                .isInstanceOf(KitComponentAlreadyInUseException.class);
    }

    @Test
    void defineKitRecipe_throwsOnSelfReference() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("KIT-001", BigDecimal.ONE))))
                .isInstanceOf(KitSelfReferenceException.class);
    }

    @Test
    void defineKitRecipe_throwsOnDuplicateComponent() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        // A primeira ocorrência de CARV-001 precisa resolver normalmente para o laço chegar até
        // a segunda, que é quem de fato aciona a checagem de duplicata.
        when(productRepository.findByAnySku("CARV-001")).thenReturn(Optional.of(simpleProduct("CARV-001", "20.00")));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001", List.of(
                new KitComponentCommand("CARV-001", BigDecimal.ONE),
                new KitComponentCommand("CARV-001", new BigDecimal("2")))))
                .isInstanceOf(DuplicateKitComponentException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentSkuDoesNotExist() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        when(productRepository.findByAnySku("FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("FANTASMA", BigDecimal.ONE))))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void defineKitRecipe_throwsWhenComponentIsNotSimples() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.isUsedAsComponent("KIT-001")).thenReturn(false);
        when(productRepository.findByAnySku("KIT-002")).thenReturn(Optional.of(kitProduct("KIT-002", "50.00")));

        assertThatThrownBy(() -> estoqueService.defineKitRecipe("KIT-001",
                List.of(new KitComponentCommand("KIT-002", BigDecimal.ONE))))
                .isInstanceOf(KitComponentNotSimpleException.class);
    }

    @Test
    void getKitRecipe_returnsCurrentRecipe() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(
                Product.of(1L, "KIT-001", "Kit", "combo", true, List.of())));
        when(kitComponentRepository.findByKitSku("KIT-001")).thenReturn(
                List.of(KitComponent.of(5L, "KIT-001", "CARV-001", new BigDecimal("2"))));

        assertThat(estoqueService.getKitRecipe("KIT-001")).hasSize(1);
    }

    @Test
    void getKitRecipe_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getKitRecipe("KIT-001"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void recordCountedItem_rejectsKitSku() {
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(
                StockCount.open(1L, "gerente")));
        when(productRepository.findByAnySku("KIT-001")).thenReturn(Optional.of(kitProduct("KIT-001", "80.00")));

        assertThatThrownBy(() -> estoqueService.recordCountedItem(1L, "KIT-001", BigDecimal.TEN))
                .isInstanceOf(KitDirectAdjustmentException.class);
    }

    @Test
    void updateProduct_rejectsCostPriceOnKit() {
        Product kit = kitProduct("KIT-001", "80.00");
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));

        assertThatThrownBy(() -> estoqueService.updateProduct("KIT-001", null, null,
                Pricing.of(new BigDecimal("40.00"), null, null)))
                .isInstanceOf(KitCostNotEditableException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProduct_allowsSalePriceChangeOnKit() {
        Product kit = kitProduct("KIT-001", "80.00");
        when(productRepository.findBySku("KIT-001")).thenReturn(Optional.of(kit));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("KIT-001", null, null,
                Pricing.of(null, null, new BigDecimal("90.00")));

        assertThat(updated.pricing().salePrice()).isEqualByComparingTo("90.00");
    }

    @Test
    void setReorderPoint_createsNewWhenNoneExists() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(reorderPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.setReorderPoint("NARG-001", "LOJA-01", new BigDecimal("10.000"));

        verify(reorderPointRepository).save(argThat(rp -> rp.id() == null
                && rp.sku().equals("NARG-001") && rp.warehouseId().equals(1L)
                && rp.minQuantity().compareTo(new BigDecimal("10.000")) == 0));
    }

    @Test
    void setReorderPoint_updatesExisting() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        ReorderPoint existing = ReorderPoint.of(5L, "NARG-001", 1L, new BigDecimal("5.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(reorderPointRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.setReorderPoint("NARG-001", "LOJA-01", new BigDecimal("10.000"));

        verify(reorderPointRepository).save(argThat(rp -> rp.id().equals(5L)
                && rp.minQuantity().compareTo(new BigDecimal("10.000")) == 0));
    }

    @Test
    void listMovements_resolvesWarehouseCodeAndDelegatesPaging() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockMovement movement = StockMovement.of(9L, "NARG-001", 1L, MovementType.SAIDA, new BigDecimal("2"),
                "Venda balcão sessão #7", "gerente", Instant.parse("2026-07-26T12:00:00Z"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockMovementRepository.findBySkuAndWarehouseId("NARG-001", 1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(movement), 0, 20, 1L, 1));

        PageResult<StockMovement> result = estoqueService.listMovements("NARG-001", "LOJA-01", 0, 20);

        assertThat(result.content()).containsExactly(movement);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(stockMovementRepository).findBySkuAndWarehouseId("NARG-001", 1L, 0, 20);
    }

    @Test
    void listMovements_returnsEmptyPageWhenSkuNeverMoved() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockMovementRepository.findBySkuAndWarehouseId("SEM-USO", 1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        PageResult<StockMovement> result = estoqueService.listMovements("SEM-USO", "LOJA-01", 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void listMovements_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listMovements("NARG-001", "INEXISTENTE", 0, 20))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(stockMovementRepository, never()).findBySkuAndWarehouseId(any(), any(), anyInt(), anyInt());
    }

    @Test
    void setReorderPoint_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.setReorderPoint("NARG-001", "INEXISTENTE", BigDecimal.TEN))
                .isInstanceOf(WarehouseNotFoundException.class);
        verify(reorderPointRepository, never()).save(any());
    }

    @Test
    void adjustStock_saida_belowReorderPoint_notifiesUsersWithStockManagePermission() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 2L);
        ReorderPoint reorderPoint = ReorderPoint.of(1L, "NARG-001", 1L, new BigDecimal("10.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(reorderPoint));
        when(userRepository.findUsernamesByPermission("ESTOQUE_STOCK_MANAGE")).thenReturn(Set.of("gerente-estoque"));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("2.000"),
                "Venda balcão", "vendedor");

        verify(notificationUseCase).notify(eq("gerente-estoque"), eq(NotificationType.SYSTEM), any(), any());
    }

    @Test
    void adjustStock_saida_aboveReorderPoint_doesNotNotify() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        StockBalance existing = StockBalance.of(10L, "NARG-001", 1L, new BigDecimal("10.000"), 2L);
        ReorderPoint reorderPoint = ReorderPoint.of(1L, "NARG-001", 1L, new BigDecimal("5.000"));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(existing));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reorderPointRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.of(reorderPoint));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA, new BigDecimal("2.000"),
                "Venda balcão", "vendedor");

        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
        verify(userRepository, never()).findUsernamesByPermission(any());
    }

    @Test
    void adjustStock_throwsWhenSkuNotInCatalog() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.adjustStock("SKU-FANTASMA", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(ProductNotFoundException.class);

        // Nada pode ser gravado — nem ledger, nem saldo. O depósito nem chega a ser resolvido.
        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
        verify(warehouseRepository, never()).findByCode(any());
    }

    @Test
    void adjustStock_acceptsVariantSkuNotJustParentSku() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(productRepository.existsBySku("NARG-M-001")).thenReturn(true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-M-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-M-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("5.000");
    }

    @Test
    void setReorderPoint_throwsWhenSkuNotInCatalog() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.setReorderPoint("SKU-FANTASMA", "LOJA-01", BigDecimal.TEN))
                .isInstanceOf(ProductNotFoundException.class);

        verify(reorderPointRepository, never()).save(any());
        verify(warehouseRepository, never()).findByCode(any());
    }

    @Test
    void adjustStock_withoutReorderPointConfigured_doesNotNotify() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA, new BigDecimal("5.000"),
                "Recebimento", "gerente");

        verify(notificationUseCase, never()).notify(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------------------------
    // EST-F018 — edição e desativação de produto e depósito
    // ------------------------------------------------------------------------------------

    private Product existingProduct() {
        return Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
    }

    @Test
    void updateProduct_alteraApenasOsCamposInformados() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Narguilé Aladin 2.0", null);

        assertThat(updated.name()).isEqualTo("Narguilé Aladin 2.0");
        assertThat(updated.category()).as("category nula significa manter").isEqualTo("narguile");
        assertThat(updated.sku()).isEqualTo("NARG-001");
        assertThat(updated.active()).isTrue();
    }

    /** PATCH não pode derrubar a grade: as variações têm que sobreviver à edição. */
    @Test
    void updateProduct_preservaVariacoes() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, "narguile-premium");

        assertThat(updated.name()).as("name nulo significa manter").isEqualTo("Narguile Aladin");
        assertThat(updated.category()).isEqualTo("narguile-premium");
        assertThat(updated.variants()).extracting(ProductVariant::sku).containsExactly("NARG-M-001");
    }

    @Test
    void updateProduct_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateProduct("SKU-FANTASMA", "Novo nome", null))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    // ---------- EST-F019 — precificação ----------

    private Product pricedProduct() {
        return Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant(),
                Pricing.of(new BigDecimal("45.00"), new BigDecimal("80"), new BigDecimal("79.90")));
    }

    @Test
    void createProduct_semPricing_nasceNaoPrecificado() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of());

        assertThat(created.pricing().isEmpty()).isTrue();
        assertThat(created.pricing().isPriced()).isFalse();
    }

    @Test
    void createProduct_comPricingNulo_naoQuebra() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of(), null);

        assertThat(created.pricing()).isEqualTo(Pricing.empty());
    }

    @Test
    void createProduct_comMarkup_persisteOPrecoSugerido() {
        when(productRepository.existsBySku(any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product created = estoqueService.createProduct("NARG-001", "Narguile", "narguile", List.of(),
                Pricing.byMarkup(new BigDecimal("45.00"), new BigDecimal("80")));

        assertThat(created.pricing().effectivePrice()).isEqualByComparingTo("81.00");
        assertThat(created.pricing().marginPercent()).isEqualByComparingTo("44.44");
    }

    @Test
    void updateProduct_pricingNulo_preservaAPrecificacaoAtual() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", "Nome Novo", null, null);

        assertThat(updated.name()).isEqualTo("Nome Novo");
        assertThat(updated.pricing().salePrice()).isEqualByComparingTo("79.90");
        assertThat(updated.pricing().costPrice()).isEqualByComparingTo("45.00");
    }

    /**
     * O caso que motiva o {@code withPatch} no service: mandar só o custo não pode apagar o
     * markup e o preço já cadastrados.
     */
    @Test
    void updateProduct_pricingParcial_naoApagaOsDemaisCampos() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.of(new BigDecimal("60.00"), null, null));

        assertThat(updated.pricing().costPrice()).isEqualByComparingTo("60.00");
        assertThat(updated.pricing().markupPercent()).as("markup preservado").isEqualByComparingTo("80");
        assertThat(updated.pricing().salePrice()).as("preço praticado preservado").isEqualByComparingTo("79.90");
        assertThat(updated.pricing().suggestedPrice()).as("sugestão acompanha o custo novo")
                .isEqualByComparingTo("108.00");
    }

    @Test
    void updateProduct_precificaProdutoQueNaoTinhaPreco() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.byMarkup(new BigDecimal("45.00"), new BigDecimal("80")));

        assertThat(updated.pricing().effectivePrice()).isEqualByComparingTo("81.00");
    }

    @Test
    void updateProduct_pricingNaoDerrubaAsVariacoes() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.updateProduct("NARG-001", null, null,
                Pricing.of(new BigDecimal("60.00"), null, null));

        assertThat(updated.variants()).extracting(ProductVariant::sku).containsExactly("NARG-M-001");
    }

    @Test
    void findPricingBySku_resolvePeloSkuPai() {
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(pricedProduct()));

        assertThat(estoqueService.findPricingBySku("NARG-001").effectivePrice())
                .isEqualByComparingTo("79.90");
    }

    /** A variação herda o preço do pai — é o caminho do leitor de código de barras no balcão. */
    @Test
    void findPricingBySku_resolveVariacaoPeloPrecoDoPai() {
        when(productRepository.findByAnySku("NARG-M-001")).thenReturn(Optional.of(pricedProduct()));

        assertThat(estoqueService.findPricingBySku("NARG-M-001").effectivePrice())
                .isEqualByComparingTo("79.90");
    }

    @Test
    void findPricingBySku_produtoSemPreco_devolveEmptyEmVezDeErro() {
        when(productRepository.findByAnySku("NARG-001")).thenReturn(Optional.of(existingProduct()));

        Pricing pricing = estoqueService.findPricingBySku("NARG-001");

        assertThat(pricing.isEmpty()).isTrue();
        assertThat(pricing.isPriced()).isFalse();
    }

    @Test
    void findPricingBySku_throwsWhenSkuNotInCatalog() {
        when(productRepository.findByAnySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.findPricingBySku("SKU-FANTASMA"))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void setProductActive_desativaPreservandoORestante() {
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(existingProduct()));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Product updated = estoqueService.setProductActive("NARG-001", false);

        assertThat(updated.active()).isFalse();
        assertThat(updated.name()).isEqualTo("Narguile Aladin");
        assertThat(updated.variants()).hasSize(1);
    }

    @Test
    void setProductActive_throwsWhenSkuNotFound() {
        when(productRepository.findBySku("SKU-FANTASMA")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.setProductActive("SKU-FANTASMA", false))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void updateWarehouse_alteraApenasOsCamposInformados() {
        Warehouse current = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(current));
        when(warehouseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Warehouse updated = estoqueService.updateWarehouse("LOJA-01", "Loja Centro Reformada", null);

        assertThat(updated.name()).isEqualTo("Loja Centro Reformada");
        assertThat(updated.type()).as("type nulo significa manter").isEqualTo(WarehouseType.LOJA_FISICA);
        assertThat(updated.code()).isEqualTo("LOJA-01");
    }

    @Test
    void updateWarehouse_throwsWhenNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.updateWarehouse("INEXISTENTE", "Nome", null))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void setWarehouseActive_desativa() {
        Warehouse current = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(current));
        when(warehouseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(estoqueService.setWarehouseActive("LOJA-01", false).active()).isFalse();
    }

    // ---- Efeito da desativação sobre a movimentação ----

    @Test
    void adjustStock_entrada_emSkuDesativado_throwsAndDoesNotPersistAnything() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(productRepository.isSkuActive("NARG-001")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(InactiveProductException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    @Test
    void adjustStock_entrada_emDepositoDesativado_throwsAndDoesNotPersistAnything() {
        Warehouse inativo = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, false);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(InactiveWarehouseException.class);

        verify(stockMovementRepository, never()).save(any());
        verify(stockBalanceRepository, never()).save(any());
    }

    /** O ponto da decisão: desativar não pode prender o saldo que ainda está na prateleira. */
    @Test
    void adjustStock_saida_emSkuDesativado_continuaPermitida() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(7L, "NARG-001", 1L, new BigDecimal("10.000"), 3L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.SAIDA,
                new BigDecimal("4.000"), "Venda", "gerente");

        assertThat(result.quantity()).isEqualByComparingTo("6.000");
        verify(productRepository, never()).isSkuActive(any());
    }

    /** AJUSTE é o caminho de correção de inventário — não pode ser barrado por desativação. */
    @Test
    void adjustStock_ajuste_emSkuDesativado_continuaPermitido() {
        Warehouse warehouse = Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(warehouse));
        when(stockBalanceRepository.findBySkuAndWarehouseId("NARG-001", 1L))
                .thenReturn(Optional.of(StockBalance.of(7L, "NARG-001", 1L, new BigDecimal("10.000"), 3L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockBalance result = estoqueService.adjustStock("NARG-001", "LOJA-01", MovementType.AJUSTE,
                new BigDecimal("2.000"), "Contagem", "gerente");

        assertThat(result.quantity()).as("EST-C009: AJUSTE é saldo-alvo, não delta")
                .isEqualByComparingTo("2.000");
        verify(productRepository, never()).isSkuActive(any());
    }

    /** SKU inexistente tem precedência: 404 antes de qualquer checagem de estado. */
    @Test
    void adjustStock_entrada_skuDesconhecido_temPrecedenciaSobreDesativado() {
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.adjustStock("SKU-FANTASMA", "LOJA-01", MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente"))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).isSkuActive(any());
    }

    @Test
    void listOrphanSkus_delegatesPagingToTheIntegrityRepository() {
        OrphanSku orphan = OrphanSku.of("SKU-FANTASMA", "LOJA-01", new BigDecimal("3.000"), 2L, false,
                Instant.parse("2026-07-01T10:00:00Z"));
        when(stockIntegrityRepository.findOrphanSkus(1, 50))
                .thenReturn(new PageResult<>(List.of(orphan), 1, 50, 1L, 1));

        PageResult<OrphanSku> result = estoqueService.listOrphanSkus(1, 50);

        assertThat(result.content()).containsExactly(orphan);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1L);
        verify(stockIntegrityRepository).findOrphanSkus(1, 50);
    }

    /** Base íntegra é o caso esperado depois de EST-C002; não é erro nem 404. */
    @Test
    void listOrphanSkus_returnsEmptyPageWhenBaseIsClean() {
        when(stockIntegrityRepository.findOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        PageResult<OrphanSku> result = estoqueService.listOrphanSkus(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    /** É diagnóstico: não pode escrever nada, nem sequer resolver depósito por código. */
    @Test
    void listOrphanSkus_doesNotTouchAnyWriteRepository() {
        when(stockIntegrityRepository.findOrphanSkus(0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        estoqueService.listOrphanSkus(0, 20);

        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(reorderPointRepository, never()).save(any());
        verify(warehouseRepository, never()).findByCode(any());
    }

    // ------------------------------------------------------------------------------------
    // EST-F006 — balanço de inventário
    // ------------------------------------------------------------------------------------

    private static final Warehouse LOJA =
            Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);

    private StockCount openCount(List<StockCountItem> items) {
        return StockCount.of(50L, 1L, StockCountStatus.ABERTA, "gerente",
                Instant.parse("2026-07-27T09:00:00Z"), null, items);
    }

    @Test
    void openStockCount_criaBalancoAbertoParaODeposito() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.findOpenByWarehouseId(1L)).thenReturn(Optional.empty());
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount opened = estoqueService.openStockCount("LOJA-01", "gerente");

        assertThat(opened.status()).isEqualTo(StockCountStatus.ABERTA);
        assertThat(opened.warehouseId()).isEqualTo(1L);
        assertThat(opened.username()).isEqualTo("gerente");
        assertThat(opened.items()).isEmpty();
        assertThat(opened.closedAt()).isNull();
    }

    /** Dois balanços no mesmo depósito contariam o mesmo saldo e se sobrescreveriam no fechamento. */
    @Test
    void openStockCount_recusaSegundoBalancoAbertoNoMesmoDeposito() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.findOpenByWarehouseId(1L)).thenReturn(Optional.of(openCount(List.of())));

        assertThatThrownBy(() -> estoqueService.openStockCount("LOJA-01", "gerente"))
                .isInstanceOf(StockCountAlreadyOpenException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void openStockCount_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.openStockCount("INEXISTENTE", "gerente"))
                .isInstanceOf(WarehouseNotFoundException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_registraOContado() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount updated = estoqueService.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"));

        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("NARG-001");
            assertThat(item.countedQuantity()).isEqualByComparingTo("37.000");
            assertThat(item.expectedQuantity()).as("só o fechamento confronta").isNull();
        });
    }

    /** Recontagem é o caso normal num balanço: sobrescreve, não vira segunda linha. */
    @Test
    void recordCountedItem_recontarSobrescreve() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(
                openCount(List.of(StockCountItem.of(9L, "NARG-001", new BigDecimal("30.000"), null, null)))));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount updated = estoqueService.recordCountedItem(50L, "NARG-001", new BigDecimal("37.000"));

        assertThat(updated.items()).singleElement().satisfies(item -> {
            assertThat(item.countedQuantity()).isEqualByComparingTo("37.000");
            assertThat(item.id()).as("reaproveita a linha existente").isEqualTo(9L);
        });
    }

    @Test
    void recordCountedItem_throwsWhenSkuNotInCatalog() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(productRepository.existsBySku("SKU-FANTASMA")).thenReturn(false);

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "SKU-FANTASMA", BigDecimal.TEN))
                .isInstanceOf(ProductNotFoundException.class);

        verify(stockCountRepository, never()).save(any());
    }

    @Test
    void recordCountedItem_throwsWhenCountNotFound() {
        when(stockCountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.recordCountedItem(99L, "NARG-001", BigDecimal.TEN))
                .isInstanceOf(StockCountNotFoundException.class);
    }

    @Test
    void recordCountedItem_throwsWhenCountAlreadyClosed() {
        StockCount fechado = StockCount.of(50L, 1L, StockCountStatus.FECHADA, "gerente",
                Instant.now(), Instant.now(), List.of());
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(fechado));

        assertThatThrownBy(() -> estoqueService.recordCountedItem(50L, "NARG-001", BigDecimal.TEN))
                .isInstanceOf(StockCountNotOpenException.class);

        verify(stockCountRepository, never()).save(any());
    }

    /** O coração do F006: divergência vira AJUSTE levando o saldo ao valor contado. */
    @Test
    void closeStockCount_aplicaAjusteApenasNosItensDivergentes() {
        StockCount count = openCount(List.of(
                StockCountItem.of(1L, "SKU-FALTA", new BigDecimal("8.000"), null, null),
                StockCountItem.of(2L, "SKU-BATEU", new BigDecimal("5.000"), null, null),
                StockCountItem.of(3L, "SKU-SOBRA", new BigDecimal("12.000"), null, null)));
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(count));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-FALTA", 1L))
                .thenReturn(Optional.of(StockBalance.of(1L, "SKU-FALTA", 1L, new BigDecimal("10.000"), 0L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-BATEU", 1L))
                .thenReturn(Optional.of(StockBalance.of(2L, "SKU-BATEU", 1L, new BigDecimal("5.000"), 0L)));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-SOBRA", 1L))
                .thenReturn(Optional.of(StockBalance.of(3L, "SKU-SOBRA", 1L, new BigDecimal("9.000"), 0L)));
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount closed = estoqueService.closeStockCount(50L, "gerente");

        assertThat(closed.status()).isEqualTo(StockCountStatus.FECHADA);
        assertThat(closed.closedAt()).isNotNull();
        assertThat(closed.items()).extracting(StockCountItem::sku, StockCountItem::expectedQuantity,
                        StockCountItem::difference)
                .containsExactly(
                        tuple("SKU-FALTA", new BigDecimal("10.000"), new BigDecimal("-2.000")),
                        tuple("SKU-BATEU", new BigDecimal("5.000"), new BigDecimal("0.000")),
                        tuple("SKU-SOBRA", new BigDecimal("9.000"), new BigDecimal("3.000")));

        // Contagem que bateu não polui o ledger: só dois movimentos, não três.
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(m ->
                assertThat(m.type()).isEqualTo(MovementType.AJUSTE));
        assertThat(captor.getAllValues()).extracting(StockMovement::sku, StockMovement::quantity)
                .containsExactly(
                        tuple("SKU-FALTA", new BigDecimal("8.000")),
                        tuple("SKU-SOBRA", new BigDecimal("12.000")));
        assertThat(captor.getAllValues()).allSatisfy(m ->
                assertThat(m.reason()).contains("Balanço de inventário #50"));
    }

    /** SKU nunca movimentado tem saldo zero implícito — contar 4 ali é sobra de 4. */
    @Test
    void closeStockCount_skuSemSaldoRegistrado_confrontaContraZero() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(
                List.of(StockCountItem.of(1L, "SKU-NOVO", new BigDecimal("4.000"), null, null)))));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-NOVO", 1L)).thenReturn(Optional.empty());
        when(stockBalanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount closed = estoqueService.closeStockCount(50L, "gerente");

        assertThat(closed.items()).singleElement().satisfies(item -> {
            assertThat(item.expectedQuantity()).isEqualByComparingTo("0");
            assertThat(item.difference()).isEqualByComparingTo("4.000");
        });
    }

    /** Contar zero é o caso do item que sumiu — e o AJUSTE tem que conseguir zerar o saldo. */
    @Test
    void closeStockCount_contagemZero_zeraOSaldo() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(
                List.of(StockCountItem.of(1L, "SKU-SUMIU", BigDecimal.ZERO, null, null)))));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockBalanceRepository.findBySkuAndWarehouseId("SKU-SUMIU", 1L))
                .thenReturn(Optional.of(StockBalance.of(1L, "SKU-SUMIU", 1L, new BigDecimal("6.000"), 0L)));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<StockBalance> captor = ArgumentCaptor.forClass(StockBalance.class);
        when(stockBalanceRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        estoqueService.closeStockCount(50L, "gerente");

        assertThat(captor.getValue().quantity()).isEqualByComparingTo("0");
    }

    @Test
    void closeStockCount_semItens_fechaSemMovimentar() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(List.of())));
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(estoqueService.closeStockCount(50L, "gerente").status())
                .isEqualTo(StockCountStatus.FECHADA);

        verify(stockMovementRepository, never()).save(any());
    }

    /** Fechar duas vezes aplicaria o mesmo ajuste em dobro. */
    @Test
    void closeStockCount_balancoJaFechado_throwsAndDoesNotAdjust() {
        StockCount fechado = StockCount.of(50L, 1L, StockCountStatus.FECHADA, "gerente",
                Instant.now(), Instant.now(), List.of());
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(fechado));

        assertThatThrownBy(() -> estoqueService.closeStockCount(50L, "gerente"))
                .isInstanceOf(StockCountNotOpenException.class);

        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void cancelStockCount_naoTocaEmSaldo() {
        when(stockCountRepository.findById(50L)).thenReturn(Optional.of(openCount(
                List.of(StockCountItem.of(1L, "NARG-001", new BigDecimal("3.000"), null, null)))));
        when(stockCountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StockCount cancelled = estoqueService.cancelStockCount(50L);

        assertThat(cancelled.status()).isEqualTo(StockCountStatus.CANCELADA);
        assertThat(cancelled.closedAt()).isNotNull();
        verify(stockBalanceRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void getStockCount_throwsWhenNotFound() {
        when(stockCountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.getStockCount(99L))
                .isInstanceOf(StockCountNotFoundException.class);
    }

    @Test
    void listStockCounts_resolveODepositoEDelegaPaginacao() {
        when(warehouseRepository.findByCode("LOJA-01")).thenReturn(Optional.of(LOJA));
        when(stockCountRepository.findByWarehouseId(1L, 0, 20))
                .thenReturn(new PageResult<>(List.of(openCount(List.of())), 0, 20, 1L, 1));

        assertThat(estoqueService.listStockCounts("LOJA-01", 0, 20).content()).hasSize(1);
        verify(stockCountRepository).findByWarehouseId(1L, 0, 20);
    }

    @Test
    void listStockCounts_throwsWhenWarehouseNotFound() {
        when(warehouseRepository.findByCode("INEXISTENTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> estoqueService.listStockCounts("INEXISTENTE", 0, 20))
                .isInstanceOf(WarehouseNotFoundException.class);
    }
}
