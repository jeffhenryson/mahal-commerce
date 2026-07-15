package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.DuplicateSkuException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class EstoqueService implements EstoqueUseCase {

    private final ProductRepository productRepository;

    public EstoqueService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
}
