package com.cernecommerce.adapter.in.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.logistica.Shipment;
import com.cernecommerce.core.ports.in.LogisticaUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

public class LogisticaControllerTest {

    private MockMvc mockMvc;
    private LogisticaUseCase logisticaUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        logisticaUseCase = mock(LogisticaUseCase.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new LogisticaController(logisticaUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listShipments_returns_200() throws Exception {
        Shipment shipment = new Shipment(1L, "PED-123", Shipment.Mode.MOTOBOY, Shipment.Status.DISPATCHED, Instant.now());
        when(logisticaUseCase.listShipments(0, 20))
                .thenReturn(new PageResult<>(List.of(shipment), 0, 20, 1L, 1));

        mockMvc.perform(get("/logistica/shipments").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].orderRef").value("PED-123"))
                .andExpect(jsonPath("$.content[0].mode").value("MOTOBOY"))
                .andExpect(jsonPath("$.content[0].status").value("DISPATCHED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
    
    @Test
    void listShipments_withPagination_passesParamsThrough() throws Exception {
        when(logisticaUseCase.listShipments(2, 50))
                .thenReturn(new PageResult<>(List.of(), 2, 50, 0L, 0));

        mockMvc.perform(get("/logistica/shipments")
                        .principal(AUTH)
                        .param("page", "2")
                        .param("size", "50"))
                .andExpect(status().isOk());
    }
}
