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

    // ── COM-F001 · cadastro de fornecedor ────────────────────────────────────────────────────

    /**
     * Permissão própria, e não COMPRAS_RECEIPT_MANAGE reaproveitada: receber mercadoria é rotina
     * de balcão, cadastrar fornecedor grava CNPJ, que é dado de compliance. Quem recebe não
     * precisa poder cadastrar.
     */
    @Test
    void register_supplier_with_receipt_manage_only_returns_403() throws Exception {
        mockMvc.perform(post("/compras/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Distribuidora Zomo LTDA\",\"taxId\":\"12345678000190\"}")
                .with(user("conferente").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("COMPRAS_RECEIPT_MANAGE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_supplier_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/compras/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Distribuidora Zomo LTDA\",\"taxId\":\"12345678000190\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patch_supplier_without_supplier_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/compras/suppliers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Novo Nome\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("COMPRAS_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_supplier_active_without_supplier_manage_returns_403() throws Exception {
        mockMvc.perform(patch("/compras/suppliers/1/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("COMPRAS_READ"))))
                .andExpect(status().isForbidden());
    }

    /** Com a authority certa, a rota é alcançada — o 404 é do id inexistente, não do RBAC. */
    @Test
    void patch_supplier_with_supplier_manage_reaches_the_route() throws Exception {
        mockMvc.perform(patch("/compras/suppliers/999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Novo Nome\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("COMPRAS_SUPPLIER_MANAGE"))))
                .andExpect(status().isNotFound());
    }
}
