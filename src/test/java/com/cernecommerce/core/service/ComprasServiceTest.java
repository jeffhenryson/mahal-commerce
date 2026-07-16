package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.compras.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComprasServiceTest {

    ComprasService comprasService;

    @BeforeEach
    void setUp() {
        comprasService = new ComprasService();
    }

    @Test
    void listSuppliers_returnsEmptyPageResultWhileStub() {
        PageResult<Supplier> result = comprasService.listSuppliers(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listSuppliers_echoesRequestedPageAndSize() {
        PageResult<Supplier> result = comprasService.listSuppliers(2, 5);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void listSuppliers_returnsImmutableContentList() {
        PageResult<Supplier> result = comprasService.listSuppliers(0, 20);

        assertThatThrownBy(() -> result.content().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listSuppliers_isConsistentAcrossCalls() {
        PageResult<Supplier> first = comprasService.listSuppliers(0, 20);
        PageResult<Supplier> second = comprasService.listSuppliers(0, 20);

        assertThat(first).isEqualTo(second);
    }
}
