package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ReorderPointRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock NotificationUseCase notificationUseCase;
    @Mock UserRepository userRepository;

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
                stockMovementRepository, reorderPointRepository, notificationUseCase, userRepository,
                immediateExecutor);
        lenient().when(reorderPointRepository.findBySkuAndWarehouseId(any(), any())).thenReturn(Optional.empty());
        // Padrão dos testes: o SKU existe no catálogo, que é a pré-condição das movimentações.
        // Os testes de createProduct e os de SKU desconhecido sobrescrevem este stub.
        lenient().when(productRepository.existsBySku(any())).thenReturn(true);
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
    void listWarehouses_delegatesToRepository() {
        List<Warehouse> warehouses = List.of(Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true));
        when(warehouseRepository.findAll()).thenReturn(warehouses);

        assertThat(estoqueService.listWarehouses()).hasSize(1);
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
}
