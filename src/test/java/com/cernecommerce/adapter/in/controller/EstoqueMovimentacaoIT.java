package com.cernecommerce.adapter.in.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo end-to-end do ledger de movimentações (GET /estoque/movements):
 * cria produto → cria depósito → registra movimentação manual → relê pelo endpoint → verifica
 * que o histórico contém a movimentação com dados corretos (sku, tipo, quantidade, username).
 *
 * <p>Prova que o ledger é persistido corretamente e que o filtro por sku/warehouseCode recupera
 * exatamente o que foi registrado.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class EstoqueMovimentacaoIT {

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
                .content("{\"code\":\"" + code + "\",\"name\":\"Deposito Movimentacao\",\"type\":\"LOJA_FISICA\"}")
                .with(user("gerente").authorities(ROLE_ADMIN, WAREHOUSE_MANAGE)))
                .andExpect(status().isCreated());
    }

    private void createProduct(MockMvc mvc, String sku) throws Exception {
        mvc.perform(post("/estoque/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"name\":\"Produto " + sku + "\",\"category\":\"testes\"}")
                .with(user("gerente").authorities(ROLE_ADMIN, PRODUCT_MANAGE)))
                .andExpect(status().isCreated());
    }

    @Test
    void movements_endpoint_persists_and_retrieves_stock_movements() throws Exception {
        MockMvc mvc = mvc();
        String warehouseCode = "WH-MOV-01";
        String sku = "MOV-SKU-001";
        String username = "movimentador";

        // Arrange: criar depósito e produto
        createWarehouse(mvc, warehouseCode);
        createProduct(mvc, sku);

        // Act: registrar uma ENTRADA manual
        String body = mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + warehouseCode + "\","
                        + "\"type\":\"ENTRADA\",\"quantity\":\"100.000\",\"reason\":\"Carga inicial\"}")
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Verificar que a resposta contém o novo saldo
        JsonNode response = om.readTree(body);
        assertThat(response.get("quantity").decimalValue()).isEqualByComparingTo(new BigDecimal("100.000"));

        // Assert: consultar o histórico via GET /estoque/movements
        String historyBody = mvc.perform(get("/estoque/movements")
                .param("sku", sku)
                .param("warehouseCode", warehouseCode)
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode history = om.readTree(historyBody);
        assertThat(history.get("content")).isNotNull();
        assertThat(history.get("content").isArray()).isTrue();
        assertThat(history.get("content").size()).isGreaterThan(0);

        // Verificar que a primeira entrada (mais recente) é a que acabamos de registrar
        JsonNode firstEntry = history.get("content").get(0);
        assertThat(firstEntry.get("sku").asText()).isEqualTo(sku);
        assertThat(firstEntry.get("type").asText()).isEqualTo("ENTRADA");
        assertThat(firstEntry.get("quantity").decimalValue()).isEqualByComparingTo(new BigDecimal("100.000"));
        assertThat(firstEntry.get("reason").asText()).isEqualTo("Carga inicial");
        assertThat(firstEntry.get("username").asText()).isEqualTo(username);
    }

    @Test
    void movements_endpoint_filters_by_sku_and_warehouse() throws Exception {
        MockMvc mvc = mvc();
        String warehouseCode1 = "WH-MOV-02";
        String warehouseCode2 = "WH-MOV-03";
        String sku1 = "MOV-SKU-002";
        String sku2 = "MOV-SKU-003";
        String username = "movimentador";

        // Arrange: criar dois depósitos e dois produtos
        createWarehouse(mvc, warehouseCode1);
        createWarehouse(mvc, warehouseCode2);
        createProduct(mvc, sku1);
        createProduct(mvc, sku2);

        // Act: registrar movimentações em diferentes combinações de sku/warehouse
        mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku1 + "\",\"warehouseCode\":\"" + warehouseCode1 + "\","
                        + "\"type\":\"ENTRADA\",\"quantity\":\"50.000\",\"reason\":\"Entrada 1\"}")
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku1 + "\",\"warehouseCode\":\"" + warehouseCode2 + "\","
                        + "\"type\":\"ENTRADA\",\"quantity\":\"75.000\",\"reason\":\"Entrada 2\"}")
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku2 + "\",\"warehouseCode\":\"" + warehouseCode1 + "\","
                        + "\"type\":\"ENTRADA\",\"quantity\":\"100.000\",\"reason\":\"Entrada sku2\"}")
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku2 + "\",\"warehouseCode\":\"" + warehouseCode1 + "\","
                        + "\"type\":\"SAIDA\",\"quantity\":\"10.000\",\"reason\":\"Saida 1\"}")
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());

        // Assert: filtrar por sku1 + warehouseCode1 deve retornar só as movimentações desse par
        String filteredBody = mvc.perform(get("/estoque/movements")
                .param("sku", sku1)
                .param("warehouseCode", warehouseCode1)
                .with(user(username).authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode filtered = om.readTree(filteredBody);
        assertThat(filtered.get("content").size()).isEqualTo(1);
        JsonNode entry = filtered.get("content").get(0);
        assertThat(entry.get("sku").asText()).isEqualTo(sku1);
        assertThat(entry.get("quantity").decimalValue()).isEqualByComparingTo(new BigDecimal("50.000"));
        assertThat(entry.get("type").asText()).isEqualTo("ENTRADA");
    }
}
