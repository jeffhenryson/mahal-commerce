package com.cernecommerce.adapter.in.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo end-to-end do alerta de estoque mínimo (F-estoque/alerta-estoque-minimo):
 * cadastra ponto de reposição → movimentação de saída cruza o mínimo → notificação
 * persistida para os usuários com {@code ESTOQUE_STOCK_MANAGE}. "admin" (seed do profile dev)
 * possui essa permissão via ROLE_ADMIN, então também é o destinatário esperado.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EstoqueAlertaIT {

    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority STOCK_MANAGE = new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE");
    private static final SimpleGrantedAuthority WAREHOUSE_MANAGE = new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE");
    private static final SimpleGrantedAuthority PRODUCT_MANAGE = new SimpleGrantedAuthority("ESTOQUE_PRODUCT_MANAGE");

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper om = new ObjectMapper();

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void createWarehouse(MockMvc mvc, String code) throws Exception {
        mvc.perform(post("/estoque/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\",\"name\":\"Depósito IT\",\"type\":\"LOJA_FISICA\"}")
                .with(user("admin").authorities(ROLE_ADMIN, WAREHOUSE_MANAGE)))
                .andExpect(status().isCreated());
    }

    /**
     * Desde EST-C002 o SKU precisa existir no catálogo antes de qualquer movimentação — sem isso
     * o {@code POST /estoque/movements} responde 404 {@code PRODUCT_NOT_FOUND}.
     */
    private void createProduct(MockMvc mvc, String sku) throws Exception {
        mvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto IT\",\"category\":\"testes\"}")
                .with(user("admin").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());
    }

    private void registerMovement(MockMvc mvc, String sku, String warehouseCode, String type, String quantity)
            throws Exception {
        mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + warehouseCode + "\",\"type\":\"" + type
                        + "\",\"quantity\":" + quantity + ",\"reason\":\"Movimentação IT\"}")
                .with(user("admin").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());
    }

    private boolean notificationBodyContains(MockMvc mvc, String sku) throws Exception {
        MvcResult result = mvc.perform(get("/notifications").param("size", "100")
                        .with(user("admin").authorities(ROLE_ADMIN)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = om.readTree(result.getResponse().getContentAsString()).get("content");
        for (JsonNode n : content) {
            if (n.get("body").asText().contains(sku)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void movimentacao_que_cruza_o_ponto_de_reposicao_dispara_notificacao() throws Exception {
        MockMvc mvc = mvc();
        String suffix = String.valueOf(System.currentTimeMillis());
        String sku = "REORDER_IT_" + suffix;
        String warehouseCode = "REORDER_WH_IT_" + suffix;

        createProduct(mvc, sku);
        createWarehouse(mvc, warehouseCode);
        registerMovement(mvc, sku, warehouseCode, "ENTRADA", "20");

        mvc.perform(put("/estoque/products/" + sku + "/reorder-point")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + warehouseCode + "\",\"minQuantity\":10}")
                .with(user("admin").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isNoContent());

        // saldo cai de 20 para 8 — abaixo do mínimo de 10
        registerMovement(mvc, sku, warehouseCode, "SAIDA", "12");

        assertThat(notificationBodyContains(mvc, sku))
                .as("Deve existir notificação mencionando o SKU %s abaixo do ponto de reposição", sku)
                .isTrue();
    }

    @Test
    void movimentacao_sem_ponto_de_reposicao_cadastrado_nao_dispara_notificacao() throws Exception {
        MockMvc mvc = mvc();
        String suffix = String.valueOf(System.currentTimeMillis() + 1);
        String sku = "NOREORDER_IT_" + suffix;
        String warehouseCode = "NOREORDER_WH_IT_" + suffix;

        createProduct(mvc, sku);
        createWarehouse(mvc, warehouseCode);
        registerMovement(mvc, sku, warehouseCode, "ENTRADA", "5");
        registerMovement(mvc, sku, warehouseCode, "SAIDA", "3");

        assertThat(notificationBodyContains(mvc, sku))
                .as("Sem ponto de reposição cadastrado, nenhuma notificação deve mencionar o SKU %s", sku)
                .isFalse();
    }
}
