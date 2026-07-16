package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdvServiceTest {

    PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService();
    }

    @Test
    void listSessions_returnsEmptyPageResultWhileStub() {
        PageResult<CashRegisterSession> result = pdvService.listSessions(0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
    }

    @Test
    void listSessions_echoesRequestedPageAndSize() {
        PageResult<CashRegisterSession> result = pdvService.listSessions(2, 5);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
    }

    @Test
    void listSessions_returnsImmutableContentList() {
        PageResult<CashRegisterSession> result = pdvService.listSessions(0, 20);

        assertThatThrownBy(() -> result.content().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void listSessions_isConsistentAcrossCalls() {
        PageResult<CashRegisterSession> first = pdvService.listSessions(0, 20);
        PageResult<CashRegisterSession> second = pdvService.listSessions(0, 20);

        assertThat(first).isEqualTo(second);
    }
}
