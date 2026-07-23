package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateWarehouseCodeException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.StockBalanceRepository;
import com.cernecommerce.core.ports.out.estoque.StockMovementRepository;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock ProductRepository productRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock StockBalanceRepository stockBalanceRepository;
    @Mock StockMovementRepository stockMovementRepository;

    EstoqueService estoqueService;

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(productRepository, warehouseRepository, stockBalanceRepository,
                stockMovementRepository);
    }

    private List<ProductVariant> oneVariant() {
        return List.of(ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "menta"))));
    }

    @Test
    void createProduct_savesAndReturns() {
        Product saved = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenReturn(saved);

        Product result = estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant());

        assertThat(result.sku()).isEqualTo("NARG-001");
        assertThat(result.variants()).hasSize(1);
        verify(productRepository).save(any());
    }

    @Test
    void createProduct_throwsWhenSkuAlreadyExists() {
        when(productRepository.findBySku("NARG-001"))
                .thenReturn(Optional.of(Product.of(1L, "NARG-001", "Existente", "narguile", true, List.of())));

        assertThatThrownBy(() -> estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant()))
                .isInstanceOf(DuplicateSkuException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProduct_allowsProductWithoutVariants() {
        Product saved = Product.of(2L, "CARV-001", "Carvão Coco", "carvao", true, List.of());
        when(productRepository.findBySku("CARV-001")).thenReturn(Optional.empty());
        when(productRepository.save(any())).thenReturn(saved);

        Product result = estoqueService.createProduct("CARV-001", "Carvão Coco", "carvao", List.of());

        assertThat(result.variants()).isEmpty();
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
}
