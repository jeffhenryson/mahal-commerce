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
public class EstoqueControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_products_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_products_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/products")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_products_with_estoque_product_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/products")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_product_without_estoque_product_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"name\":\"Narguile Aladin\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_product_with_estoque_product_manage_returns_201() throws Exception {
        String sku = "NARG_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Narguile Aladin\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_warehouses_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/warehouses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_warehouses_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/warehouses")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_warehouses_with_estoque_warehouse_read_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/warehouses")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void create_warehouse_without_estoque_warehouse_manage_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"LOJA-SEC\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_warehouse_with_estoque_warehouse_manage_returns_201() throws Exception {
        String code = "LOJA_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void get_stock_balance_with_estoque_warehouse_read_returns_200_or_404() throws Exception {
        // Sem depósito prévio cadastrado com esse código, a resposta é 404 (WAREHOUSE_NOT_FOUND) —
        // o importante aqui é a authority ser aceita (200/404), não bloqueada por 401/403.
        mockMvc.perform(get("/estoque/stock-balance")
                .param("sku", "NARG-001")
                .param("warehouseCode", "INEXISTENTE_SEC_TEST")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_stock_balance_without_estoque_warehouse_read_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/stock-balance")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }
}
