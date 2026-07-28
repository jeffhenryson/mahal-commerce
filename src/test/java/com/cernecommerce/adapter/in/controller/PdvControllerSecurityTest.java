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

@SpringBootTest
@ActiveProfiles("dev")
public class PdvControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_sessions_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/pdv/sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_sessions_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sessions")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_sessions_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/sessions")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk());
    }

    /**
     * PDV-F004: sem {@code unitPrice} — o preço é resolvido pelo servidor.
     * PDV-C004: sem {@code warehouseCode} — o depósito vem da sessão de caixa.
     */
    private static final String SALE_BODY = "{\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1}]}";

    /** Mesma venda, com desconto — exige a permissão PDV_SALE_DISCOUNT além de PDV_SALE_MANAGE. */
    private static final String SALE_BODY_WITH_DISCOUNT =
            "{\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1,\"discountAmount\":1.00}]}";

    private static final String OPEN_SESSION_BODY =
            "{\"openingAmount\":200.00,\"warehouseCode\":\"LOJA-01\"}";

    private static final String MOVEMENT_BODY =
            "{\"type\":\"SANGRIA\",\"amount\":50.00,\"reason\":\"cofre\"}";

    private static final String CLOSE_BODY = "{\"countedAmount\":495.00}";

    @Test
    void register_sale_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_sale_without_pdv_sale_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY)
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_sale_with_pdv_sale_manage_and_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    /**
     * PDV-F004: vender e conceder desconto não são a mesma autorização. Registrar venda é operação
     * de caixa; abater valor é decisão comercial.
     */
    @Test
    void register_sale_with_discount_but_without_pdv_sale_discount_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY_WITH_DISCOUNT)
                .with(user("caixa").authorities(
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    /**
     * Com a permissão de desconto, o pedido passa da checagem de autorização e só então esbarra na
     * sessão inexistente — provando que o 403 acima veio do desconto, não da rota.
     */
    @Test
    void register_sale_with_discount_and_pdv_sale_discount_reaches_the_service() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/sales")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SALE_BODY_WITH_DISCOUNT)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("PDV_SALE_MANAGE"),
                        new SimpleGrantedAuthority("PDV_SALE_DISCOUNT"))))
                .andExpect(status().isNotFound());
    }

    // ── Ciclo de caixa (PDV-F001/F002) ───────────────────────────────────────────────────────

    @Test
    void open_session_without_pdv_session_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(OPEN_SESSION_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void open_session_with_unknown_warehouse_returns_404() throws Exception {
        // Passou da autorização e esbarrou no depósito — prova que o 403 acima veio da permissão.
        mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"openingAmount\":200.00,\"warehouseCode\":\"NAO-EXISTE\"}")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void open_session_without_opening_amount_returns_400() throws Exception {
        mockMvc.perform(post("/pdv/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\"}")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void current_session_without_open_register_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sessions/current")
                .with(user("sem-caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void register_movement_without_pdv_session_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MOVEMENT_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_movement_without_reason_returns_400() throws Exception {
        // Sangria sem motivo é indistinguível de desvio — a validação é de negócio, não estética.
        mockMvc.perform(post("/pdv/sessions/999999/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"SANGRIA\",\"amount\":50.00}")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void close_session_requires_its_own_permission() throws Exception {
        // PDV_SESSION_MANAGE abre e movimenta, mas não fecha: a conferência é do gerente.
        mockMvc.perform(post("/pdv/sessions/999999/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CLOSE_BODY)
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SESSION_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_nonexistent_session_with_permission_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CLOSE_BODY)
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_SESSION_CLOSE"))))
                .andExpect(status().isNotFound());
    }

    // ── Liquidar no balcão pedido feito no app ───────────────────────────────────────────────

    @Test
    void pending_online_orders_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/pending-online-orders")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pending_online_orders_with_pdv_read_returns_200() throws Exception {
        mockMvc.perform(get("/pdv/pending-online-orders")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void settle_online_order_without_pdv_sale_manage_returns_403() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/orders/7/settle")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void settle_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(post("/pdv/sessions/999999/orders/7/settle")
                .with(user("caixa").authorities(new SimpleGrantedAuthority("PDV_SALE_MANAGE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_order_without_pdv_read_returns_403() throws Exception {
        mockMvc.perform(get("/pdv/sales/1")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_nonexistent_order_with_pdv_read_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sales/999999")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_session_orders_with_nonexistent_session_returns_404() throws Exception {
        mockMvc.perform(get("/pdv/sessions/999999/sales")
                .with(user("gerente").authorities(new SimpleGrantedAuthority("PDV_READ"))))
                .andExpect(status().isNotFound());
    }
}
