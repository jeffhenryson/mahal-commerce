package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

class PdvComandaControllerTest {

    private MockMvc mockMvc;
    private ComandaUseCase comandaUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("caixa1", null, List.of());

    @BeforeEach
    void setup() {
        comandaUseCase = mock(ComandaUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvComandaController(comandaUseCase, new ComandaDTOConverter(),
                        new OrderDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Comanda abertaComanda() {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA, List.of(), null,
                "caixa1", Instant.now(), null);
    }

    @Test
    void openComanda_returns_201() throws Exception {
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("ABERTA"));
    }

    @Test
    void addItem_returns_201_withRunningTotal() throws Exception {
        Comanda withItem = abertaComanda().withAddedItem(
                ComandaItem.fromCatalog("ESS-MENTA", BigDecimal.ONE,
                        Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")), "Essência Menta"));
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), anyString())).thenReturn(withItem);

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].sku").value("ESS-MENTA"))
                .andExpect(jsonPath("$.runningTotal").value(25.00));
    }

    @Test
    void getComanda_returns_200() throws Exception {
        when(comandaUseCase.getComanda(10L)).thenReturn(abertaComanda());

        mockMvc.perform(get("/pdv/comandas/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableOrCustomerLabel").value("Mesa 4"));
    }

    @Test
    void getComanda_notFound_returns_404() throws Exception {
        when(comandaUseCase.getComanda(999L)).thenThrow(new ComandaNotFoundException(999L));

        mockMvc.perform(get("/pdv/comandas/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    @Test
    void listOpenComandas_returns_200() throws Exception {
        when(comandaUseCase.listOpenComandas(1L)).thenReturn(List.of(abertaComanda()));

        mockMvc.perform(get("/pdv/comandas?sessionId=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void closeComanda_returns_200_withConcludedOrder() throws Exception {
        Order order = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        OrderItem.of(1L, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                                new BigDecimal("10.00"), BigDecimal.ZERO, null, "Essência Menta")))
                .concluded("000001000", null, Instant.now());
        when(comandaUseCase.closeComanda(eq(10L), any(), anyString())).thenReturn(order);

        mockMvc.perform(post("/pdv/comandas/10/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":25.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    @Test
    void cancelComanda_returns_200() throws Exception {
        Comanda cancelada = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.CANCELADA, List.of(),
                null, "caixa1", Instant.now(), Instant.now());
        when(comandaUseCase.cancelComanda(eq(10L), anyString())).thenReturn(cancelada);

        mockMvc.perform(post("/pdv/comandas/10/cancel").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
    }
}
