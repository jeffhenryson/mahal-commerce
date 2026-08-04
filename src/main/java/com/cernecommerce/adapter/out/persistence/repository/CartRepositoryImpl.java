package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CartEntity;
import com.cernecommerce.adapter.out.persistence.entity.CartItemEntity;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.domain.model.ecommerce.CartItem;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
@Transactional
public class CartRepositoryImpl implements CartRepository {

    private final CartJpaRepository cartJpaRepository;

    public CartRepositoryImpl(CartJpaRepository cartJpaRepository) {
        this.cartJpaRepository = cartJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findByCustomerId(Long customerId) {
        return cartJpaRepository.findByCustomerId(customerId).map(this::toDomain);
    }

    @Override
    public Cart upsertItem(Long customerId, String sku, BigDecimal quantity) {
        CartEntity cart = cartJpaRepository.findByCustomerId(customerId).orElseGet(() -> {
            CartEntity created = new CartEntity();
            created.setCustomerId(customerId);
            created.setCreatedAt(Instant.now());
            created.setUpdatedAt(Instant.now());
            return created;
        });

        Optional<CartItemEntity> existing = cart.getItems().stream()
                .filter(item -> item.getSku().equals(sku))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setQuantity(quantity);
        } else {
            CartItemEntity item = new CartItemEntity();
            item.setCart(cart);
            item.setSku(sku);
            item.setQuantity(quantity);
            cart.getItems().add(item);
        }
        cart.setUpdatedAt(Instant.now());
        return toDomain(cartJpaRepository.save(cart));
    }

    @Override
    public boolean removeItem(Long customerId, String sku) {
        Optional<CartEntity> cartOpt = cartJpaRepository.findByCustomerId(customerId);
        if (cartOpt.isEmpty()) {
            return false;
        }
        CartEntity cart = cartOpt.get();
        boolean removed = cart.getItems().removeIf(item -> item.getSku().equals(sku));
        if (removed) {
            cart.setUpdatedAt(Instant.now());
            cartJpaRepository.save(cart);
        }
        return removed;
    }

    @Override
    public void clear(Long customerId) {
        cartJpaRepository.findByCustomerId(customerId).ifPresent(cart -> {
            if (!cart.getItems().isEmpty()) {
                cart.getItems().clear();
                cart.setUpdatedAt(Instant.now());
                cartJpaRepository.save(cart);
            }
        });
    }

    private Cart toDomain(CartEntity e) {
        var items = e.getItems().stream()
                .map(i -> new CartItem(i.getSku(), i.getQuantity()))
                .toList();
        return Cart.of(e.getId(), e.getCustomerId(), items, e.getUpdatedAt());
    }
}
