package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcommerceServiceTest {

    EcommerceService ecommerceService;

    @BeforeEach
    void setUp() {
        ecommerceService = new EcommerceService();
    }

    @Test
    void listCarts_returnsEmptyPageResultWhileStub() {
        PageResult<Cart> result = ecommerceService.listCarts(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listCarts_echoesRequestedPageAndSize() {
        PageResult<Cart> result = ecommerceService.listCarts(2, 5);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void listCarts_returnsImmutableContentList() {
        PageResult<Cart> result = ecommerceService.listCarts(0, 20);

        assertThatThrownBy(() -> result.content().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listCarts_isConsistentAcrossCalls() {
        PageResult<Cart> first = ecommerceService.listCarts(0, 20);
        PageResult<Cart> second = ecommerceService.listCarts(0, 20);

        assertThat(first).isEqualTo(second);
    }
}
