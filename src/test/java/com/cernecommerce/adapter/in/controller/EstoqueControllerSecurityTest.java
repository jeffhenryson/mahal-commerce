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

    /**
     * Cadastra um SKU único e o devolve. Desde EST-C002 movimentar saldo ou definir ponto de
     * reposição exige que o SKU exista no catálogo, então os testes de escrita precisam criar o
     * produto antes — só checar a authority não basta mais para chegar ao 2xx.
     */
    private String givenProduct() throws Exception {
        String sku = "SKU_SEC_TEST_" + System.nanoTime();
        mockMvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto Teste\",\"category\":\"testes\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isCreated());
        return sku;
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

    @Test
    void register_movement_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                        + "\"quantity\":1,\"reason\":\"teste\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_movement_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"NARG-001\",\"warehouseCode\":\"LOJA-01\",\"type\":\"ENTRADA\","
                        + "\"quantity\":1,\"reason\":\"teste\"}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_movement_with_estoque_stock_manage_returns_201() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_MOV_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + code + "\",\"type\":\"ENTRADA\","
                        + "\"quantity\":1,\"reason\":\"teste\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.warehouseCode").value(code));
    }

    @Test
    void set_reorder_point_without_auth_returns_401() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void set_reorder_point_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(put("/estoque/products/NARG-001/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"LOJA-01\",\"minQuantity\":10}")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void set_reorder_point_with_estoque_stock_manage_returns_204() throws Exception {
        String sku = givenProduct();
        String code = "LOJA_REORDER_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/estoque/products/" + sku + "/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\",\"minQuantity\":10}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void list_movements_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_movements_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /** Ler o ledger expõe quem movimentou o quê, então exige STOCK_MANAGE — não basta WAREHOUSE_READ. */
    @Test
    void list_movements_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", "LOJA-01")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_movements_with_estoque_stock_manage_returns_200() throws Exception {
        String code = "LOJA_MOVLIST_SEC_TEST_" + System.currentTimeMillis();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/estoque/movements")
                .param("sku", "NARG-001")
                .param("warehouseCode", code)
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }

    // EST-F018 — PATCH e desativação reusam as permissões de MANAGE do respectivo recurso.

    @Test
    void patch_product_without_auth_returns_401() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Ler o catálogo não dá direito de editá-lo. */
    @Test
    void patch_product_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_with_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Produto Renomeado\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_product_active_with_product_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/products/QUALQUER-SKU/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_product_active_with_product_manage_returns_200() throws Exception {
        String sku = givenProduct();

        mockMvc.perform(patch("/estoque/products/" + sku + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE"))))
                .andExpect(status().isOk());
    }

    @Test
    void patch_warehouse_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(patch("/estoque/warehouses/QUALQUER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Novo\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void patch_warehouse_and_active_with_warehouse_manage_returns_200() throws Exception {
        String code = "LOJA_PATCH_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/estoque/warehouses/" + code)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Loja Renomeada\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/estoque/warehouses/" + code + "/active")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isOk());
    }

    // EST-F006 — o balanço reusa ESTOQUE_STOCK_MANAGE: fechar uma contagem é movimentar saldo.

    @Test
    void open_stock_count_without_auth_returns_401() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"QUALQUER\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Ver saldo não dá direito de abrir balanço — fechar um aplica ajuste. */
    @Test
    void open_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"QUALQUER\"}")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/stock-counts/1")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_stock_count_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(post("/estoque/stock-counts/1/close")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void open_stock_count_with_estoque_stock_manage_returns_201() throws Exception {
        String code = "LOJA_COUNT_SEC_" + System.nanoTime();
        mockMvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Loja Teste\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\"}")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isCreated());
    }

    @Test
    void list_orphan_skus_without_auth_returns_401() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void list_orphan_skus_with_user_role_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                .with(user("bob").authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    /**
     * O diagnóstico de integridade expõe o passivo de dados sujos do estoque, então segue a mesma
     * régua do ledger: exige STOCK_MANAGE, e WAREHOUSE_READ não basta.
     */
    @Test
    void list_orphan_skus_with_warehouse_read_only_returns_403() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                .with(user("bob").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_orphan_skus_with_estoque_stock_manage_returns_200() throws Exception {
        mockMvc.perform(get("/estoque/integrity/orphan-skus")
                .with(user("gerente").authorities(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE"))))
                .andExpect(status().isOk());
    }
}
