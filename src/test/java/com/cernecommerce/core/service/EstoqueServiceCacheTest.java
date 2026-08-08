package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
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
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@EnableCaching
@Import(EstoqueServiceCacheTest.CacheConfig.class)
class EstoqueServiceCacheTest {

    @TestConfiguration
    static class CacheConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("shopCatalog");
        }

        @Bean
        EstoqueService estoqueService(ProductRepository productRepository, WarehouseRepository warehouseRepository,
                StockBalanceRepository stockBalanceRepository, StockMovementRepository stockMovementRepository,
                ReorderPointRepository reorderPointRepository, StockIntegrityRepository stockIntegrityRepository,
                StockCountRepository stockCountRepository, StockReservationRepository stockReservationRepository,
                NotificationUseCase notificationUseCase, UserRepository userRepository,
                KitComponentRepository kitComponentRepository, StockLotRepository stockLotRepository,
                SystemConfigPort systemConfigPort) {
            return new EstoqueService(productRepository, warehouseRepository, stockBalanceRepository,
                    stockMovementRepository, reorderPointRepository, stockIntegrityRepository, stockCountRepository,
                    stockReservationRepository, notificationUseCase, userRepository,
                    new AfterCommitExecutor() {
                        @Override
                        public <T> void accumulate(String key, T item, Consumer<List<T>> flush) {
                            flush.accept(List.of(item));
                        }
                    },
                    Duration.ofMinutes(30), kitComponentRepository, stockLotRepository, systemConfigPort);
        }
    }

    @MockitoBean ProductRepository productRepository;
    @MockitoBean WarehouseRepository warehouseRepository;
    @MockitoBean StockBalanceRepository stockBalanceRepository;
    @MockitoBean StockMovementRepository stockMovementRepository;
    @MockitoBean ReorderPointRepository reorderPointRepository;
    @MockitoBean StockIntegrityRepository stockIntegrityRepository;
    @MockitoBean StockCountRepository stockCountRepository;
    @MockitoBean StockReservationRepository stockReservationRepository;
    @MockitoBean NotificationUseCase notificationUseCase;
    @MockitoBean UserRepository userRepository;
    @MockitoBean KitComponentRepository kitComponentRepository;
    @MockitoBean StockLotRepository stockLotRepository;
    @MockitoBean SystemConfigPort systemConfigPort;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    private EstoqueUseCase estoqueService;

    @BeforeEach
    void setUp() {
        Cache cache = cacheManager.getCache("shopCatalog");
        if (cache != null) cache.clear();

        lenient().when(productRepository.existsBySku(any())).thenReturn(true);
        lenient().when(productRepository.isSkuActive(any())).thenReturn(true);
    }

    private List<ProductVariant> oneVariant() {
        return List.of(ProductVariant.create("NARG-M-001", List.of(new ProductAttribute("sabor", "menta"))));
    }

    // ── Cache tests for listActivePricedProducts ───────────────────────────────────────────────

    @Test
    void listActivePricedProducts_second_call_returns_from_cache() {
        PageResult<Product> result = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(productRepository.findAllActiveAndPriced(0, 20, false))
                .thenReturn(result);

        estoqueService.listActivePricedProducts(0, 20, false);
        estoqueService.listActivePricedProducts(0, 20, false);

        verify(productRepository, times(1)).findAllActiveAndPriced(0, 20, false);
    }

    @Test
    void listActivePricedProducts_different_parameters_hit_separate_cache_entries() {
        PageResult<Product> page0 = new PageResult<>(List.of(), 0, 20, 0L, 0);
        PageResult<Product> page1 = new PageResult<>(List.of(), 1, 20, 0L, 0);
        when(productRepository.findAllActiveAndPriced(0, 20, false)).thenReturn(page0);
        when(productRepository.findAllActiveAndPriced(1, 20, false)).thenReturn(page1);

        estoqueService.listActivePricedProducts(0, 20, false);
        estoqueService.listActivePricedProducts(0, 20, false);
        estoqueService.listActivePricedProducts(1, 20, false);
        estoqueService.listActivePricedProducts(1, 20, false);

        verify(productRepository, times(1)).findAllActiveAndPriced(0, 20, false);
        verify(productRepository, times(1)).findAllActiveAndPriced(1, 20, false);
    }

    @Test
    void listActivePricedProducts_onSale_parameter_affects_cache_key() {
        PageResult<Product> onSaleTrue = new PageResult<>(List.of(), 0, 20, 5L, 0);
        PageResult<Product> onSaleFalse = new PageResult<>(List.of(), 0, 20, 20L, 0);
        when(productRepository.findAllActiveAndPriced(0, 20, true)).thenReturn(onSaleTrue);
        when(productRepository.findAllActiveAndPriced(0, 20, false)).thenReturn(onSaleFalse);

        estoqueService.listActivePricedProducts(0, 20, true);
        estoqueService.listActivePricedProducts(0, 20, true);
        estoqueService.listActivePricedProducts(0, 20, false);
        estoqueService.listActivePricedProducts(0, 20, false);

        verify(productRepository, times(1)).findAllActiveAndPriced(0, 20, true);
        verify(productRepository, times(1)).findAllActiveAndPriced(0, 20, false);
    }

    // ── Cache eviction tests ─────────────────────────────────────────────────────────────────

    @Test
    void createProduct_evicts_shopCatalog_cache() {
        PageResult<Product> beforeCreate = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(productRepository.existsBySku(any())).thenReturn(false);
        Product newProduct = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, oneVariant());
        when(productRepository.save(any())).thenReturn(newProduct);
        when(productRepository.findAllActiveAndPriced(0, 20, false)).thenReturn(beforeCreate);

        // Prime the cache
        estoqueService.listActivePricedProducts(0, 20, false);
        verify(productRepository).findAllActiveAndPriced(0, 20, false);

        // Create a product — should evict cache
        estoqueService.createProduct("NARG-001", "Narguile Aladin", "narguile", oneVariant(),
                Pricing.empty(), "Aladin", "http://img.png", false);

        // Second call should go to DB again
        estoqueService.listActivePricedProducts(0, 20, false);
        verify(productRepository, times(2)).findAllActiveAndPriced(0, 20, false);
    }

    @Test
    void updateProduct_evicts_shopCatalog_cache() {
        PageResult<Product> result = new PageResult<>(List.of(), 0, 20, 0L, 0);
        Product product = Product.of(1L, "NARG-001", "Narguile Old", "narguile", true, oneVariant());
        when(productRepository.findAllActiveAndPriced(0, 20, false)).thenReturn(result);
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Prime the cache
        estoqueService.listActivePricedProducts(0, 20, false);
        verify(productRepository).findAllActiveAndPriced(0, 20, false);

        // Update a product — should evict cache
        estoqueService.updateProduct("NARG-001", "Narguile New", "narguile", null, "Aladin", "http://img.png", null);

        // Second call should go to DB again
        estoqueService.listActivePricedProducts(0, 20, false);
        verify(productRepository, times(2)).findAllActiveAndPriced(0, 20, false);
    }

    @Test
    void setProductActive_evicts_shopCatalog_cache() {
        PageResult<Product> result = new PageResult<>(List.of(), 0, 20, 0L, 0);
        Product product = Product.of(1L, "NARG-001", "Narguile", "narguile", true, oneVariant());
        when(productRepository.findAllActiveAndPriced(0, 20, false)).thenReturn(result);
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Prime the cache
        estoqueService.listActivePricedProducts(0, 20, false);
        verify(productRepository).findAllActiveAndPriced(0, 20, false);

        // Set product inactive — should evict cache
        estoqueService.setProductActive("NARG-001", false);

        // Second call should go to DB again
        estoqueService.listActivePricedProducts(0, 20, false);
        verify(productRepository, times(2)).findAllActiveAndPriced(0, 20, false);
    }

    @Test
    void multiple_mutations_all_evict_cache() {
        PageResult<Product> result = new PageResult<>(List.of(), 0, 20, 0L, 0);
        Product product = Product.of(1L, "NARG-001", "Narguile", "narguile", true, oneVariant());
        when(productRepository.findAllActiveAndPriced(0, 20, false)).thenReturn(result);
        when(productRepository.findBySku("NARG-001")).thenReturn(Optional.of(product));
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Prime the cache
        estoqueService.listActivePricedProducts(0, 20, false);

        // Sequence of mutations
        estoqueService.setProductActive("NARG-001", false);
        estoqueService.listActivePricedProducts(0, 20, false);

        estoqueService.updateProduct("NARG-001", "Narguile", "narguile", null, "Aladin", "http://img.png", null);
        estoqueService.listActivePricedProducts(0, 20, false);

        // Each evict should cause a fresh fetch
        verify(productRepository, times(3)).findAllActiveAndPriced(0, 20, false);
    }
}
