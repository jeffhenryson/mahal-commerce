package com.cernecommerce.adapter.in.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo end-to-end do balanço de inventário (EST-F006) com o AJUSTE de saldo-alvo (EST-C009):
 * abre o balanço → conta três SKUs (um faltando, um batendo, um sobrando) → fecha → confere que
 * só os divergentes moveram saldo e que o ledger registrou o AJUSTE.
 *
 * <p>É o teste que prova a integração inteira: sem ele, service e controller poderiam estar certos
 * isoladamente e o saldo continuar errado no banco.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
class EstoqueInventarioIT {

    private static final SimpleGrantedAuthority ROLE_ADMIN = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority STOCK_MANAGE = new SimpleGrantedAuthority("ESTOQUE_STOCK_MANAGE");
    private static final SimpleGrantedAuthority WAREHOUSE_MANAGE = new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_MANAGE");
    private static final SimpleGrantedAuthority WAREHOUSE_READ = new SimpleGrantedAuthority("ESTOQUE_WAREHOUSE_READ");
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
                .content("{\"code\":\"" + code + "\",\"name\":\"Deposito Inventario\",\"type\":\"LOJA_FISICA\"}")
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

    private void entrada(MockMvc mvc, String sku, String code, String quantity) throws Exception {
        mvc.perform(post("/estoque/movements")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"warehouseCode\":\"" + code + "\",\"type\":\"ENTRADA\","
                        + "\"quantity\":" + quantity + ",\"reason\":\"Carga inicial\"}")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated());
    }

    private BigDecimal saldo(MockMvc mvc, String sku, String code) throws Exception {
        String body = mvc.perform(get("/estoque/stock-balance")
                .param("sku", sku).param("warehouseCode", code)
                .with(user("gerente").authorities(ROLE_ADMIN, WAREHOUSE_READ)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(om.readTree(body).get("quantity").asText());
    }

    private void contar(MockMvc mvc, long countId, String sku, String quantity) throws Exception {
        mvc.perform(post("/estoque/stock-counts/" + countId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"" + sku + "\",\"countedQuantity\":" + quantity + "}")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk());
    }

    private long abrirBalanco(MockMvc mvc, String code) throws Exception {
        String body = mvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\"}")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("id").asLong();
    }

    @Test
    void balanco_ajustaApenasOsDivergentesEDeixaTrilhaNoLedger() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_INV_" + nonce;
        String falta = "INV_FALTA_" + nonce;
        String bateu = "INV_BATEU_" + nonce;
        String sobra = "INV_SOBRA_" + nonce;

        createWarehouse(mvc, code);
        for (String sku : new String[]{falta, bateu, sobra}) {
            createProduct(mvc, sku);
        }
        entrada(mvc, falta, code, "10.000");
        entrada(mvc, bateu, code, "5.000");
        entrada(mvc, sobra, code, "9.000");

        long countId = abrirBalanco(mvc, code);
        contar(mvc, countId, falta, "8.000");
        contar(mvc, countId, bateu, "5.000");
        contar(mvc, countId, sobra, "12.000");

        mvc.perform(post("/estoque/stock-counts/" + countId + "/close")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FECHADA"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.warehouseCode").value(code));

        // O saldo foi levado ao valor contado, para baixo e para cima.
        assertThat(saldo(mvc, falta, code)).isEqualByComparingTo("8.000");
        assertThat(saldo(mvc, bateu, code)).isEqualByComparingTo("5.000");
        assertThat(saldo(mvc, sobra, code)).isEqualByComparingTo("12.000");

        // Divergente ganhou um AJUSTE no ledger, além da ENTRADA inicial.
        assertThat(movimentos(mvc, falta, code)).hasSize(2);
        assertThat(movimentos(mvc, falta, code).get(0).get("type").asText()).isEqualTo("AJUSTE");

        // Contagem que bateu não gerou movimentação nenhuma além da carga inicial.
        assertThat(movimentos(mvc, bateu, code)).hasSize(1);
        assertThat(movimentos(mvc, bateu, code).get(0).get("type").asText()).isEqualTo("ENTRADA");
    }

    private JsonNode movimentos(MockMvc mvc, String sku, String code) throws Exception {
        String body = mvc.perform(get("/estoque/movements")
                .param("sku", sku).param("warehouseCode", code)
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("content");
    }

    /** Contar zero é o item que sumiu — o AJUSTE tem que conseguir zerar o saldo. */
    @Test
    void balanco_contagemZero_zeraOSaldo() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_INV0_" + nonce;
        String sku = "INV_SUMIU_" + nonce;

        createWarehouse(mvc, code);
        createProduct(mvc, sku);
        entrada(mvc, sku, code, "6.000");

        long countId = abrirBalanco(mvc, code);
        contar(mvc, countId, sku, "0");

        mvc.perform(post("/estoque/stock-counts/" + countId + "/close")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].expectedQuantity").value(6.0))
                .andExpect(jsonPath("$.items[0].difference").value(-6.0));

        assertThat(saldo(mvc, sku, code)).isEqualByComparingTo("0");
    }

    @Test
    void balanco_segundoAbertoNoMesmoDeposito_returns_409() throws Exception {
        MockMvc mvc = mvc();
        String code = "WH_INV2_" + System.nanoTime();
        createWarehouse(mvc, code);
        abrirBalanco(mvc, code);

        mvc.perform(post("/estoque/stock-counts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseCode\":\"" + code + "\"}")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_ALREADY_OPEN"));
    }

    /** Cancelar libera o depósito para um novo balanço e não encosta no saldo. */
    @Test
    void balanco_cancelado_naoAjustaSaldoELiberaODeposito() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_INVC_" + nonce;
        String sku = "INV_CANC_" + nonce;

        createWarehouse(mvc, code);
        createProduct(mvc, sku);
        entrada(mvc, sku, code, "7.000");

        long countId = abrirBalanco(mvc, code);
        contar(mvc, countId, sku, "2.000");

        mvc.perform(post("/estoque/stock-counts/" + countId + "/cancel")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));

        assertThat(saldo(mvc, sku, code)).as("cancelar não toca em saldo").isEqualByComparingTo("7.000");

        // Depósito liberado: dá para abrir outro.
        abrirBalanco(mvc, code);
    }

    @Test
    void balanco_fecharDuasVezes_returns_409() throws Exception {
        MockMvc mvc = mvc();
        String code = "WH_INVF_" + System.nanoTime();
        createWarehouse(mvc, code);
        long countId = abrirBalanco(mvc, code);

        mvc.perform(post("/estoque/stock-counts/" + countId + "/close")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk());

        mvc.perform(post("/estoque/stock-counts/" + countId + "/close")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STOCK_COUNT_NOT_OPEN"));
    }

    @Test
    void balanco_contarSkuForaDoCatalogo_returns_404() throws Exception {
        MockMvc mvc = mvc();
        String code = "WH_INVX_" + System.nanoTime();
        createWarehouse(mvc, code);
        long countId = abrirBalanco(mvc, code);

        mvc.perform(post("/estoque/stock-counts/" + countId + "/items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU_FANTASMA_999\",\"countedQuantity\":3}")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    /** Recontar sobrescreve: o fechamento usa o último valor, não o primeiro. */
    @Test
    void balanco_recontagemSobrescreve() throws Exception {
        MockMvc mvc = mvc();
        long nonce = System.nanoTime();
        String code = "WH_INVR_" + nonce;
        String sku = "INV_RECONT_" + nonce;

        createWarehouse(mvc, code);
        createProduct(mvc, sku);
        entrada(mvc, sku, code, "10.000");

        long countId = abrirBalanco(mvc, code);
        contar(mvc, countId, sku, "3.000");
        contar(mvc, countId, sku, "7.000");

        mvc.perform(get("/estoque/stock-counts/" + countId)
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].countedQuantity").value(7.0));

        mvc.perform(post("/estoque/stock-counts/" + countId + "/close")
                .with(user("gerente").authorities(ROLE_ADMIN, STOCK_MANAGE)))
                .andExpect(status().isOk());

        assertThat(saldo(mvc, sku, code)).isEqualByComparingTo("7.000");
    }
}
