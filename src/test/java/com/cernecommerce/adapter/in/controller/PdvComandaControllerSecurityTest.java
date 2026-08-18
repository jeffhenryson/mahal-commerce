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
 * PDV-F009: mesmo padrão de {@code PdvControllerSecurityTest} — sessão/comanda inexistente
 * (999999) é suficiente para exercitar 403/404 sem precisar montar um ciclo de caixa real.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PdvComandaControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String OPEN_BODY = "{\"tableOrCustomerLabel\":\"Mesa 4\"}";
    private static final String ADD_ITEM_BODY = "{\"sku\":\"NARG-001\",\"quantity\":1}";
    private static final String CLOSE_BODY = "{\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":1}]}";

    // ── Abrir comanda ────────────────────────────────────────────────────────────────────────

    @Test
    void open_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void open_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void open_comanda_with_permission_and_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas?sessionId=999999")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(OPEN_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CASH_REGISTER_SESSION_NOT_FOUND"));
    }

    // ── Lançar item ──────────────────────────────────────────────────────────────────────────

    @Test
    void add_item_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/items")
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_ITEM_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void add_item_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/items")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_ITEM_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void add_item_with_permission_and_nonexistent_comanda_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/items")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(ADD_ITEM_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    // ── Consulta ─────────────────────────────────────────────────────────────────────────────

    @Test
    void get_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_comanda_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_nonexistent_comanda_with_pdv_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/comandas/999999")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    @Test
    void list_open_comandas_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/comandas?sessionId=999999")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_open_comandas_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/comandas?sessionId=999999")
                        .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk());
    }

    // ── Fechar ───────────────────────────────────────────────────────────────────────────────

    @Test
    void close_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void close_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_nonexistent_comanda_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/close")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE")))
                        .contentType(MediaType.APPLICATION_JSON).content(CLOSE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }

    // ── Cancelar ─────────────────────────────────────────────────────────────────────────────

    @Test
    void cancel_comanda_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void cancel_comanda_without_pdv_comanda_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/cancel")
                        .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancel_nonexistent_comanda_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/comandas/999999/cancel")
                        .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_COMANDA_MANAGE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMANDA_NOT_FOUND"));
    }
}
