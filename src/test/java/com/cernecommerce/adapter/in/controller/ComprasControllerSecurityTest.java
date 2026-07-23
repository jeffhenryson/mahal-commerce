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
public class ComprasControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void list_suppliers_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/compras/suppliers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_suppliers_without_compras_read_returns_403() throws Exception {
        mockMvc.perform(get("/compras/suppliers")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_suppliers_with_compras_read_returns_200() throws Exception {
        mockMvc.perform(get("/compras/suppliers")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("COMPRAS_READ"))))
                .andExpect(status().isOk());
    }

    @Test
    void receive_goods_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/compras/goods-receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":1,\"warehouseCode\":\"LOJA-01\","
                        + "\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void receive_goods_without_compras_receipt_manage_returns_403() throws Exception {
        mockMvc.perform(post("/compras/goods-receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":1,\"warehouseCode\":\"LOJA-01\","
                        + "\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1}]}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("COMPRAS_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void receive_goods_with_compras_receipt_manage_returns_404_when_supplier_not_found() throws Exception {
        // Sem fornecedor cadastrado com esse id — o importante aqui é a authority ser aceita
        // (404, não bloqueada por 401/403).
        mockMvc.perform(post("/compras/goods-receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":999999,\"warehouseCode\":\"LOJA-01\","
                        + "\"items\":[{\"sku\":\"NARG-001\",\"quantity\":1}]}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("COMPRAS_RECEIPT_MANAGE"))))
                .andExpect(status().isNotFound());
    }
}
