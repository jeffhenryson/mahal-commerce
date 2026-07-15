package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EstoqueServiceTest {

    @Mock ProductRepository productRepository;

    EstoqueService estoqueService;

    @BeforeEach
    void setUp() {
        estoqueService = new EstoqueService(productRepository);
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
}
