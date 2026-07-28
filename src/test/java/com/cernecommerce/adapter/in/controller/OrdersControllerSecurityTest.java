package com.cernecommerce.adapter.in.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * As três permissões de {@code /orders} são separadas porque as operações têm consequências
 * diferentes: ler é inócuo, avançar estágio é expedição, e cancelar <b>devolve mercadoria ao
 * estoque</b>. Estes testes provam que uma não vale pela outra.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class OrdersControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    private static final String STATUS_BODY = "{\"status\":\"SEPARADO\"}";
    private static final String CANCEL_BODY = "{\"reason\":\"cliente desistiu\"}";

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_orders_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void list_orders_without_order_read_returns_403() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orders_with_order_read_returns_200() throws Exception {
        mockMvc.perform(get("/orders")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_orders_accepts_every_filter() throws Exception {
        mockMvc.perform(get("/orders")
                .param("channel", "MARKETPLACE")
                .param("status", "PAGO")
                .param("customerId", "42")
                .param("from", "2026-07-01T00:00:00Z")
                .param("to", "2026-07-31T23:59:59Z")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void list_orders_rejects_invalid_channel() throws Exception {
        mockMvc.perform(get("/orders")
                .param("channel", "TELEPATIA")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_orders_rejects_size_above_limit() throws Exception {
        mockMvc.perform(get("/orders")
                .param("size", "101")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_nonexistent_order_returns_404() throws Exception {
        mockMvc.perform(get("/orders/999999")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void change_status_with_order_read_only_returns_403() throws Exception {
        // Ler não autoriza despachar.
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void change_status_with_order_fulfill_reaches_the_service() throws Exception {
        mockMvc.perform(post("/orders/999999/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(STATUS_BODY)
                .with(user("expedicao").authorities(new SimpleGrantedAuthority("ORDER_FULFILL"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_with_order_fulfill_returns_403() throws Exception {
        // Despachar não autoriza cancelar: o cancelamento devolve mercadoria ao estoque.
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("expedicao").authorities(new SimpleGrantedAuthority("ORDER_FULFILL"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_with_order_cancel_reaches_the_service() throws Exception {
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CANCEL_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_without_reason_returns_400() throws Exception {
        // Estorno sem justificativa registrada é indistinguível de erro.
        mockMvc.perform(post("/orders/999999/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("ORDER_CANCEL"))))
                .andExpect(status().isBadRequest());
    }
}
