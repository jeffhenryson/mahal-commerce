package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class PdvComandaControllerTest {

    private MockMvc mockMvc;
    private ComandaUseCase comandaUseCase;
    private CrmUseCase crmUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("caixa1", null, List.of());

    /** Cortesia é desconto de 100%, e tem permissão própria (PDV-F010) — só ADMIN a recebe. */
    private static final UsernamePasswordAuthenticationToken AUTH_COURTESY =
            new UsernamePasswordAuthenticationToken("gerente", null,
                    List.of(new SimpleGrantedAuthority("PDV_COMANDA_COURTESY")));

    @BeforeEach
    void setup() {
        comandaUseCase = mock(ComandaUseCase.class);
        crmUseCase = mock(CrmUseCase.class);
        when(crmUseCase.findCustomerNames(anyCollection())).thenReturn(Map.of());
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvComandaController(comandaUseCase, new ComandaDTOConverter(),
                        new OrderDTOConverter(), crmUseCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Comanda abertaComanda() {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA, List.of(), null,
                "caixa1", Instant.now(), null);
    }

    @Test
    void openComanda_returns_201() throws Exception {
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), any(), anyString())).thenReturn(abertaComanda());

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
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(), anyString()))
                .thenReturn(withItem);

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
    // ── Cortesia: a permissão que o controller guarda (PDV-F010) ─────────────────────────────

    @Test
    void addItem_courtesyWithoutAuthority_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-UVA\",\"quantity\":1,\"mode\":\"SABOR_EXTRA\","
                                + "\"courtesy\":true,\"linkedItemId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COURTESY_NOT_ALLOWED"));

        // Recusa ANTES do service: nada pode ter sido debitado do estoque.
        verify(comandaUseCase, never()).addItem(any(), any(), any(), any(), anyBoolean(), any(), any());
    }

    /** {@code TROCA} é cortesia por definição — não depende do cliente ter marcado o campo. */
    @Test
    void addItem_trocaWithoutAuthority_returns_403_evenWithoutTheCourtesyFlag() throws Exception {
        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-UVA\",\"quantity\":1,\"mode\":\"TROCA\","
                                + "\"linkedItemId\":7}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COURTESY_NOT_ALLOWED"));
    }

    @Test
    void addItem_courtesyWithAuthority_returns_201_andForwardsTheSessionFields() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("SESS-UVA"), any(), eq(ConsumptionMode.SABOR_EXTRA),
                eq(true), eq(7L), anyString())).thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH_COURTESY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"SESS-UVA\",\"quantity\":1,\"mode\":\"SABOR_EXTRA\","
                                + "\"courtesy\":true,\"linkedItemId\":7}"))
                .andExpect(status().isCreated());

        verify(comandaUseCase).addItem(eq(10L), eq("SESS-UVA"), any(), eq(ConsumptionMode.SABOR_EXTRA),
                eq(true), eq(7L), eq("gerente"));
    }

    /** Item comum continua passando sem a permissão — o gate é só da linha a zero. */
    @Test
    void addItem_withoutCourtesy_doesNotRequireTheAuthority() throws Exception {
        when(comandaUseCase.addItem(eq(10L), eq("ESS-MENTA"), any(), any(), eq(false), any(), anyString()))
                .thenReturn(abertaComanda());

        mockMvc.perform(post("/pdv/comandas/10/items")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"ESS-MENTA\",\"quantity\":1}"))
                .andExpect(status().isCreated());
    }

    @Test
    void openComanda_withCustomer_resolvesTheNameFromCrm() throws Exception {
        Comanda comCliente = Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", 42L, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", Instant.now(), null);
        when(comandaUseCase.openComanda(eq(1L), eq("Mesa 4"), eq(42L), anyString())).thenReturn(comCliente);
        when(crmUseCase.findCustomerNames(anyCollection())).thenReturn(Map.of(42L, "Ana"));

        mockMvc.perform(post("/pdv/comandas?sessionId=1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableOrCustomerLabel\":\"Mesa 4\",\"customerId\":42}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(42))
                .andExpect(jsonPath("$.customerName").value("Ana"));
    }
}
