package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.logistica.Shipment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisticaServiceTest {

    LogisticaService logisticaService;

    @BeforeEach
    void setUp() {
        logisticaService = new LogisticaService();
    }

    @Test
    void listShipments_returnsEmptyPageResultWhileStub() {
        PageResult<Shipment> result = logisticaService.listShipments(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listShipments_echoesRequestedPageAndSize() {
        PageResult<Shipment> result = logisticaService.listShipments(2, 5);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void listShipments_returnsImmutableContentList() {
        PageResult<Shipment> result = logisticaService.listShipments(0, 20);

        assertThatThrownBy(() -> result.content().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listShipments_isConsistentAcrossCalls() {
        PageResult<Shipment> first = logisticaService.listShipments(0, 20);
        PageResult<Shipment> second = logisticaService.listShipments(0, 20);

        assertThat(first).isEqualTo(second);
    }
}
